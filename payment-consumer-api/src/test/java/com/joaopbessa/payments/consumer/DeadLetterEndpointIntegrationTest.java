package com.joaopbessa.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.joaopbessa.payments.consumer.config.KafkaConsumerProperties;
import com.joaopbessa.payments.common.avro.PaymentEvent;
import com.joaopbessa.payments.common.avro.PaymentMethod;
import com.joaopbessa.payments.common.observability.PrometheusText;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import({ PostgresContainerConfiguration.class, ConsumerDatabaseRoleConfiguration.class,
        KafkaInfrastructureConfiguration.class })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Dead letter endpoint (Kafka + Apicurio + Postgres integration)")
class DeadLetterEndpointIntegrationTest {

    private static final String URL = "/api/v1/dead-letters";
    private static final String DEAD_LETTERED_METRIC = "payments_dead_lettered_total";
    private static final String CONSUMER_GROUP = "payment-persistence";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(40);
    private static final int MAX_MESSAGES = 10;
    private static final int EVENTS_PUBLISHED = 11;
    private static final String UNKNOWN_SOURCE = "000000";
    private static final String UNKNOWN_TARGET = "000001";
    private static final String POISON_TEXT = "this is not an avro payload";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

    @Autowired
    private KafkaConsumerProperties properties;

    @Value("${spring.kafka.consumer.properties.apicurio.registry.url}")
    private String registryUrl;

    private KafkaTestClients clients;

    @BeforeEach
    void setUp() {
        clients = new KafkaTestClients(kafkaConnectionDetails.getProducer().getBootstrapServers(), registryUrl,
                properties.topics().paymentEvents(), properties.topics().paymentEventsDlt());
    }

    @AfterEach
    void tearDown() {
        clients.close();
    }

    private PaymentEvent newEvent() {
        return PaymentEvent.newBuilder()
                .setTransactionId(UUID.randomUUID().toString())
                .setEventTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .setSourceAccount(UNKNOWN_SOURCE)
                .setTargetAccount(UNKNOWN_TARGET)
                .setAmount(new BigDecimal("42.00"))
                .setCurrency("USD")
                .setPaymentMethod(PaymentMethod.PIX)
                .build();
    }

    private double deadLettered() {
        String scrape = restTemplate.getForObject("/actuator/prometheus", String.class);
        return new PrometheusText(scrape).sum(DEAD_LETTERED_METRIC);
    }

    private JsonNode deadLetters() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(URL, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return jsonMapper.readTree(response.getBody());
    }

    private Set<String> consumerGroups() throws ExecutionException, InterruptedException {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers",
                kafkaConnectionDetails.getConsumer().getBootstrapServers()))) {
            return admin.listConsumerGroups().all().get().stream()
                    .map(ConsumerGroupListing::groupId)
                    .collect(Collectors.toSet());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should return an empty list while nothing was dead-lettered")
    void shouldReturnAnEmptyListWhileNothingWasDeadLettered() throws Exception {
        JsonNode messages = deadLetters();

        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("Should return only the ten oldest dead letters, oldest first, with their payloads")
    void shouldReturnOnlyTheTenOldestDeadLettersOldestFirstWithTheirPayloads() throws Exception {
        double before = deadLettered();
        List<PaymentEvent> events = new ArrayList<>();
        clients.sendRaw("poison", POISON_TEXT.getBytes(StandardCharsets.UTF_8));
        for (int index = 0; index < EVENTS_PUBLISHED; index++) {
            PaymentEvent event = newEvent();
            events.add(event);
            clients.sendEvent(event.getTransactionId(), event);
        }
        await().atMost(AWAIT_TIMEOUT).untilAsserted(
                () -> assertThat(deadLettered() - before).isEqualTo(EVENTS_PUBLISHED + 1));

        JsonNode messages = deadLetters();

        assertThat(messages.size()).isEqualTo(MAX_MESSAGES);
        assertThat(messages.get(0).get("key").asString()).isEqualTo("poison");
        assertThat(messages.get(0).get("payload").isNull()).isTrue();
        assertThat(messages.get(0).get("raw_payload").asString()).isEqualTo(POISON_TEXT);
        for (int index = 1; index < MAX_MESSAGES; index++) {
            PaymentEvent expected = events.get(index - 1);
            JsonNode message = messages.get(index);
            assertThat(message.get("offset").asLong()).isGreaterThan(messages.get(index - 1).get("offset").asLong());
            assertThat(message.get("key").asString()).isEqualTo(expected.getTransactionId());
            assertThat(message.get("raw_payload").isNull()).isTrue();
            JsonNode payload = message.get("payload");
            assertThat(payload.get("transaction_id").asString()).isEqualTo(expected.getTransactionId());
            assertThat(payload.get("source_account").asString()).isEqualTo(UNKNOWN_SOURCE);
            assertThat(payload.get("target_account").asString()).isEqualTo(UNKNOWN_TARGET);
            assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("42.00");
            assertThat(payload.get("currency").asString()).isEqualTo("USD");
            assertThat(payload.get("payment_method").asString()).isEqualTo("pix");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Should read the dead letters without leaving a consumer group behind")
    void shouldReadTheDeadLettersWithoutLeavingAConsumerGroupBehind() throws Exception {
        deadLetters();
        deadLetters();

        assertThat(consumerGroups()).containsExactly(CONSUMER_GROUP);
    }

}
