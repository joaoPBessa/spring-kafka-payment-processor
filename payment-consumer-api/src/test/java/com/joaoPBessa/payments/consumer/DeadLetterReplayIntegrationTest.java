package com.joaoPBessa.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.joaoPBessa.payments.consumer.config.KafkaConsumerProperties;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterReplayService;
import com.joaoPBessa.payments.consumer.dlt.ReplaySummary;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.avro.PaymentMethod;
import com.joaopBessa.payments.common.observability.PrometheusText;

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
@DisplayName("Dead letter replay (Kafka + Apicurio + Postgres integration)")
class DeadLetterReplayIntegrationTest {

    private static final String DEAD_LETTERS_URL = "/api/v1/dead-letters";
    private static final String REPLAY_URL = "/api/v1/dead-letters/replay";
    private static final String REPLAYED_METRIC = "payments_dead_letter_replayed_total";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(40);
    private static final Duration DLT_POLL_WINDOW = Duration.ofSeconds(3);
    private static final int DLT_PARTITION = 0;

    private static final String INSERT_ACCOUNT =
            "INSERT INTO account (id, number, name) VALUES (?, ?, ?) ON CONFLICT (number) DO NOTHING";
    private static final String COUNT_PAYMENTS = "SELECT count(*) FROM payment WHERE transaction_id = ?";

    private static final String FIRST_SOURCE = "500001";
    private static final String FIRST_TARGET = "500002";
    private static final String SECOND_SOURCE = "500101";
    private static final String SECOND_TARGET = "500102";

    private static final List<String> forcedIds = new ArrayList<>();
    private static final List<String> backlogIds = new ArrayList<>();
    private static long startOffsetBeforeTheJob;
    private static long endOffsetBeforeTheJob;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate adminJdbcTemplate;

    @Autowired
    private DeadLetterReplayService replayService;

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

    private PaymentEvent publishEventBetween(String source, String target) {
        PaymentEvent event = PaymentEvent.newBuilder()
                .setTransactionId(UUID.randomUUID().toString())
                .setEventTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .setSourceAccount(source)
                .setTargetAccount(target)
                .setAmount(new BigDecimal("42.00"))
                .setCurrency("USD")
                .setPaymentMethod(PaymentMethod.PIX)
                .build();
        clients.sendEvent(event.getTransactionId(), event);
        return event;
    }

    private void createAccounts(String... numbers) {
        for (String number : numbers) {
            adminJdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), number, "Account " + number);
        }
    }

    private int countPayments(String transactionId) {
        return adminJdbcTemplate.queryForObject(COUNT_PAYMENTS, Integer.class, transactionId);
    }

    private void awaitPersisted(String transactionId) {
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(countPayments(transactionId)).isEqualTo(1));
    }

    private JsonNode deadLetters() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(DEAD_LETTERS_URL, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return jsonMapper.readTree(response.getBody());
    }

    private Set<String> deadLetterKeys() throws Exception {
        return StreamSupport.stream(deadLetters().spliterator(), false)
                .map(message -> message.get("key").asString())
                .collect(Collectors.toSet());
    }

    private void awaitDeadLetterKeys(Set<String> expected) {
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(deadLetterKeys()).isEqualTo(expected));
    }

    private JsonNode replayRequest(List<String> transactionIds) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                REPLAY_URL, Map.of("transaction_ids", transactionIds), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return jsonMapper.readTree(response.getBody()).get("results");
    }

    private double replays(String trigger, String result) {
        String scrape = restTemplate.getForObject("/actuator/prometheus", String.class);
        return new PrometheusText(scrape).sum(REPLAYED_METRIC, "trigger=\"" + trigger + "\"",
                "result=\"" + result + "\"");
    }

    private long dltOffset(OffsetSpec spec) throws ExecutionException, InterruptedException {
        TopicPartition partition = new TopicPartition(properties.topics().paymentEventsDlt(), DLT_PARTITION);
        try (Admin admin = Admin.create(Map.of("bootstrap.servers",
                kafkaConnectionDetails.getAdmin().getBootstrapServers()))) {
            return admin.listOffsets(Map.of(partition, spec)).partitionResult(partition).get().offset();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should replay only the requested ids and report each one")
    void shouldReplayOnlyTheRequestedIdsAndReportEachOne() throws Exception {
        PaymentEvent first = publishEventBetween(FIRST_SOURCE, FIRST_TARGET);
        PaymentEvent second = publishEventBetween(FIRST_SOURCE, FIRST_TARGET);
        forcedIds.addAll(List.of(first.getTransactionId(), second.getTransactionId()));
        awaitDeadLetterKeys(Set.copyOf(forcedIds));
        createAccounts(FIRST_SOURCE, FIRST_TARGET);
        double manualBefore = replays("manual", "success");

        JsonNode results = replayRequest(List.of(first.getTransactionId(), "unknown-transaction"));

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0).get("transaction_id").asString()).isEqualTo(first.getTransactionId());
        assertThat(results.get(0).get("status").asString()).isEqualTo("replayed");
        assertThat(results.get(0).get("partition").asInt()).isEqualTo(DLT_PARTITION);
        assertThat(results.get(0).get("offset").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(results.get(1).get("transaction_id").asString()).isEqualTo("unknown-transaction");
        assertThat(results.get(1).get("status").asString()).isEqualTo("not_found");
        awaitPersisted(first.getTransactionId());
        assertThat(countPayments(second.getTransactionId())).isZero();
        assertThat(replays("manual", "success") - manualBefore).isEqualTo(1.0);
    }

    @Test
    @Order(2)
    @DisplayName("Should replay the remaining id and leave the dead letters in the topic")
    void shouldReplayTheRemainingIdAndLeaveTheDeadLettersInTheTopic() throws Exception {
        String secondId = forcedIds.get(1);

        JsonNode results = replayRequest(List.of(secondId));

        assertThat(results.get(0).get("status").asString()).isEqualTo("replayed");
        awaitPersisted(secondId);
        assertThat(deadLetterKeys()).containsAll(forcedIds);
    }

    @Test
    @Order(3)
    @DisplayName("Should send back to the dead letter topic what still fails and clear what was replayed")
    void shouldSendBackToTheDeadLetterTopicWhatStillFailsAndClearWhatWasReplayed() throws Exception {
        PaymentEvent third = publishEventBetween(SECOND_SOURCE, SECOND_TARGET);
        PaymentEvent fourth = publishEventBetween(SECOND_SOURCE, SECOND_TARGET);
        backlogIds.addAll(List.of(third.getTransactionId(), fourth.getTransactionId()));
        Set<String> everything = new HashSet<>(forcedIds);
        everything.addAll(backlogIds);
        awaitDeadLetterKeys(everything);
        startOffsetBeforeTheJob = dltOffset(OffsetSpec.earliest());
        endOffsetBeforeTheJob = dltOffset(OffsetSpec.latest());
        double scheduledBefore = replays("scheduled", "success");

        ReplaySummary summary = replayService.replayPending();

        assertThat(summary).isEqualTo(new ReplaySummary(4, 0));
        assertThat(replays("scheduled", "success") - scheduledBefore).isEqualTo(4.0);
        awaitDeadLetterKeys(Set.copyOf(backlogIds));
        JsonNode remaining = deadLetters();
        assertThat(remaining.size()).isEqualTo(2);
        for (JsonNode message : remaining) {
            assertThat(message.get("offset").asLong()).isGreaterThanOrEqualTo(endOffsetBeforeTheJob);
        }
        assertThat(dltOffset(OffsetSpec.earliest())).isGreaterThan(startOffsetBeforeTheJob);
        for (String forcedId : forcedIds) {
            assertThat(countPayments(forcedId)).isEqualTo(1);
        }
    }

    @Test
    @Order(4)
    @DisplayName("Should not accumulate dead letter headers when a message fails again")
    void shouldNotAccumulateDeadLetterHeadersWhenAMessageFailsAgain() {
        List<ConsumerRecord<String, byte[]>> deadLetters = clients.pollDeadLetters(backlogIds.get(0), DLT_POLL_WINDOW);

        assertThat(deadLetters).hasSize(1);
        assertThat(deadLetters.get(0).headers().headers(KafkaHeaders.DLT_ORIGINAL_TOPIC)).hasSize(1);
        assertThat(deadLetters.get(0).headers().headers(KafkaHeaders.DLT_EXCEPTION_FQCN)).hasSize(1);
    }

    @Test
    @Order(5)
    @DisplayName("Should persist the backlog and empty the dead letter topic once the cause is fixed")
    void shouldPersistTheBacklogAndEmptyTheDeadLetterTopicOnceTheCauseIsFixed() throws Exception {
        createAccounts(SECOND_SOURCE, SECOND_TARGET);

        ReplaySummary summary = replayService.replayPending();

        assertThat(summary).isEqualTo(new ReplaySummary(2, 0));
        for (String backlogId : backlogIds) {
            awaitPersisted(backlogId);
        }
        assertThat(deadLetters().size()).isZero();
    }

}
