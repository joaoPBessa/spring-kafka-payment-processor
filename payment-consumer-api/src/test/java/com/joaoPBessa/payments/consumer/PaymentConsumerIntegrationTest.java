package com.joaoPBessa.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.joaoPBessa.payments.consumer.config.KafkaConsumerProperties;
import com.joaoPBessa.payments.consumer.domain.entities.Payment;
import com.joaoPBessa.payments.consumer.repositories.PaymentRepository;
import com.joaoPBessa.payments.consumer.services.PaymentPersistenceService;
import com.joaopBessa.payments.common.avro.PaymentEvent;

@SpringBootTest
@ActiveProfiles("test")
@Import({ PostgresContainerConfiguration.class, KafkaInfrastructureConfiguration.class })
@DisplayName("Payment consumer (Kafka + Apicurio + Postgres integration)")
class PaymentConsumerIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(40);
    private static final Duration DLT_POLL_WINDOW = Duration.ofSeconds(3);
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(2);

    private static final String INSERT_ACCOUNT =
            "INSERT INTO account (id, number, name) VALUES (?, ?, ?) ON CONFLICT (number) DO NOTHING";
    private static final String COUNT_PAYMENTS = "SELECT count(*) FROM payment WHERE transaction_id = ?";
    private static final String SELECT_PAYMENT = "SELECT * FROM payment WHERE transaction_id = ?";

    private static final String SOURCE_ACCOUNT = "300001";
    private static final String TARGET_ACCOUNT = "300002";
    private static final String UNKNOWN_ACCOUNT = "000000";
    private static final BigDecimal AMOUNT = new BigDecimal("150.75");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

    @Autowired
    private KafkaConsumerProperties properties;

    @Value("${spring.kafka.consumer.properties.apicurio.registry.url}")
    private String registryUrl;

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private PaymentPersistenceService paymentPersistenceService;

    private KafkaTestClients clients;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), SOURCE_ACCOUNT, "Source");
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), TARGET_ACCOUNT, "Target");
        clients = new KafkaTestClients(kafkaConnectionDetails.getProducer().getBootstrapServers(), registryUrl,
                properties.topics().paymentEvents(), properties.topics().paymentEventsDlt());
    }

    @AfterEach
    void tearDown() {
        clients.close();
    }

    private PaymentEvent newEvent(String sourceAccount, BigDecimal amount) {
        return PaymentEvent.newBuilder()
                .setTransactionId(UUID.randomUUID().toString())
                .setEventTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .setSourceAccount(sourceAccount)
                .setTargetAccount(TARGET_ACCOUNT)
                .setAmount(amount)
                .setCurrency("USD")
                .setPaymentMethod(com.joaopBessa.payments.common.avro.PaymentMethod.PIX)
                .build();
    }

    private void publish(PaymentEvent event) {
        clients.sendEvent(event.getTransactionId(), event);
    }

    private int countPayments(String transactionId) {
        return jdbcTemplate.queryForObject(COUNT_PAYMENTS, Integer.class, transactionId);
    }

    private void awaitPersisted(String transactionId) {
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(countPayments(transactionId)).isEqualTo(1));
    }

    private List<ConsumerRecord<String, byte[]>> awaitDeadLetters(String key) {
        List<ConsumerRecord<String, byte[]>> found = new java.util.ArrayList<>();
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            found.clear();
            found.addAll(clients.pollDeadLetters(key, DLT_POLL_WINDOW));
            assertThat(found).hasSize(1);
        });
        return found;
    }

    private String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private Payment forTransaction(String transactionId) {
        return argThat(payment -> payment != null && transactionId.equals(payment.getTransactionId()));
    }

    private PaymentEvent eventForTransaction(String transactionId) {
        return argThat(event -> event != null && transactionId.equals(event.getTransactionId()));
    }

    @Test
    @DisplayName("Should persist a valid event with every field")
    void shouldPersistValidEventWithEveryField() {
        PaymentEvent event = newEvent(SOURCE_ACCOUNT, AMOUNT);

        publish(event);
        awaitPersisted(event.getTransactionId());

        Map<String, Object> row = jdbcTemplate.queryForMap(SELECT_PAYMENT, event.getTransactionId());
        assertThat(row.get("source_account")).isEqualTo(SOURCE_ACCOUNT);
        assertThat(row.get("target_account")).isEqualTo(TARGET_ACCOUNT);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(AMOUNT);
        assertThat(row.get("currency")).isEqualTo("USD");
        assertThat(row.get("payment_method")).isEqualTo("PIX");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(clients.pollDeadLetters(event.getTransactionId(), QUIET_WINDOW)).isEmpty();
    }

    @Test
    @DisplayName("Should keep a single row when the same event is delivered twice")
    void shouldKeepSingleRowWhenSameEventIsDeliveredTwice() {
        PaymentEvent event = newEvent(SOURCE_ACCOUNT, AMOUNT);
        PaymentEvent marker = newEvent(SOURCE_ACCOUNT, AMOUNT);

        publish(event);
        publish(event);
        publish(marker);
        awaitPersisted(marker.getTransactionId());

        assertThat(countPayments(event.getTransactionId())).isEqualTo(1);
        assertThat(clients.pollDeadLetters(event.getTransactionId(), QUIET_WINDOW)).isEmpty();
    }

    @Test
    @DisplayName("Should dead-letter an event for an unknown account without retrying")
    void shouldDeadLetterEventForUnknownAccountWithoutRetrying() {
        PaymentEvent event = newEvent(UNKNOWN_ACCOUNT, AMOUNT);

        publish(event);
        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetters(event.getTransactionId()).get(0);

        assertThat(clients.deserialize(deadLetter.value())).isEqualTo(event);
        assertThat(deadLetter.key()).isEqualTo(event.getTransactionId());
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(properties.topics().paymentEvents());
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .isEqualTo(ListenerExecutionFailedException.class.getName());
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(DataIntegrityViolationException.class.getName());
        assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
        assertThat(countPayments(event.getTransactionId())).isZero();
        verify(paymentRepository, times(1)).saveAndFlush(forTransaction(event.getTransactionId()));
    }

    @Test
    @DisplayName("Should dead-letter an event that violates the positive amount rule without retrying")
    void shouldDeadLetterEventThatViolatesPositiveAmountRuleWithoutRetrying() {
        PaymentEvent event = newEvent(SOURCE_ACCOUNT, new BigDecimal("0.00"));

        publish(event);
        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetters(event.getTransactionId()).get(0);

        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(DataIntegrityViolationException.class.getName());
        assertThat(countPayments(event.getTransactionId())).isZero();
        verify(paymentRepository, times(1)).saveAndFlush(forTransaction(event.getTransactionId()));
    }

    @Test
    @DisplayName("Should retry a transient database failure and dead-letter it when attempts are exhausted")
    void shouldRetryTransientFailureAndDeadLetterWhenAttemptsAreExhausted() {
        PaymentEvent event = newEvent(SOURCE_ACCOUNT, AMOUNT);
        doThrow(new QueryTimeoutException("database unavailable"))
                .when(paymentRepository).saveAndFlush(forTransaction(event.getTransactionId()));

        publish(event);
        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetters(event.getTransactionId()).get(0);

        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(QueryTimeoutException.class.getName());
        assertThat(clients.deserialize(deadLetter.value())).isEqualTo(event);
        assertThat(countPayments(event.getTransactionId())).isZero();
        verify(paymentRepository, times(properties.retry().maxAttempts()))
                .saveAndFlush(forTransaction(event.getTransactionId()));
    }

    @Test
    @DisplayName("Should persist the payment when a transient failure clears before attempts are exhausted")
    void shouldPersistPaymentWhenTransientFailureClearsBeforeAttemptsAreExhausted() {
        PaymentEvent event = newEvent(SOURCE_ACCOUNT, AMOUNT);
        doThrow(new QueryTimeoutException("first failure"))
                .doThrow(new QueryTimeoutException("second failure"))
                .doCallRealMethod()
                .when(paymentPersistenceService).persist(eventForTransaction(event.getTransactionId()));

        publish(event);
        awaitPersisted(event.getTransactionId());

        verify(paymentPersistenceService, times(3)).persist(eventForTransaction(event.getTransactionId()));
        assertThat(clients.pollDeadLetters(event.getTransactionId(), QUIET_WINDOW)).isEmpty();
    }

    @Test
    @DisplayName("Should dead-letter a poison pill with its original bytes and keep consuming")
    void shouldDeadLetterPoisonPillWithOriginalBytesAndKeepConsuming() {
        String poisonKey = "poison-" + UUID.randomUUID();
        byte[] poisonPayload = "this is not an avro payload".getBytes(StandardCharsets.UTF_8);
        PaymentEvent validEvent = newEvent(SOURCE_ACCOUNT, AMOUNT);

        clients.sendRaw(poisonKey, poisonPayload);
        publish(validEvent);
        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetters(poisonKey).get(0);
        awaitPersisted(validEvent.getTransactionId());

        assertThat(deadLetter.value()).isEqualTo(poisonPayload);
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_FQCN)).isEqualTo(DeserializationException.class.getName());
        assertThat(deadLetter.headers().lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNull();
    }

}
