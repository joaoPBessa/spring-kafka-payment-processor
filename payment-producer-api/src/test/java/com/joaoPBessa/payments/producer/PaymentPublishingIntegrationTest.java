package com.joaoPBessa.payments.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.PaymentResponseDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;
import com.joaoPBessa.payments.producer.repositories.AccountRepository;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.domain.PaymentMethod;
import com.joaopBessa.payments.common.observability.PrometheusText;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@AutoConfigureTracing
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("Payment publishing (Testcontainers integration)")
class PaymentPublishingIntegrationTest {

    private static final String TRACE_PARENT_HEADER = "traceparent";

    @Value("${spring.kafka.producer.properties.apicurio.registry.url}")
    private String registryUrl;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

    private ConsumerRecord<String, PaymentEvent> awaitRecord(KafkaConsumer<String, PaymentEvent> consumer,
            String transactionId) {
        List<ConsumerRecord<String, PaymentEvent>> found = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            for (ConsumerRecord<String, PaymentEvent> record : consumer.poll(Duration.ofMillis(500))) {
                if (transactionId.equals(record.value().getTransactionId())) {
                    found.add(record);
                }
            }
            assertThat(found).hasSize(1);
        });
        return found.get(0);
    }

    private Account createActiveAccount(String number) {
        return accountRepository.save(Account.builder()
                .id(UUID.randomUUID())
                .number(number)
                .name("Integration Test Account " + number)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Should publish the exact Avro-encoded PaymentEvent for a request accepted over HTTP")
    void shouldRoundTripPaymentEventThroughRealKafkaAndApicurio() {
        createActiveAccount("111111");
        createActiveAccount("222222");
        var request = new PaymentRequestDTO("111111", "222222", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        ResponseEntity<PaymentResponseDTO> httpResponse =
                restTemplate.postForEntity("/api/v1/payments", request, PaymentResponseDTO.class);

        assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String transactionId = httpResponse.getBody().transactionCode();

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnectionDetails.getConsumer().getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-publishing-integration-test");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class);
        consumerProps.put("apicurio.registry.url", registryUrl);
        consumerProps.put("apicurio.registry.use-specific-avro-reader", "true");

        try (KafkaConsumer<String, PaymentEvent> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("payment.events"));
            ConsumerRecord<String, PaymentEvent> record = awaitRecord(consumer, transactionId);
            PaymentEvent event = record.value();

            assertThat(record.headers().toArray()).extracting(Header::key).contains(TRACE_PARENT_HEADER);
            assertThat(event.getTransactionId()).isEqualTo(transactionId);
            assertThat(event.getSourceAccount()).isEqualTo("111111");
            assertThat(event.getTargetAccount()).isEqualTo("222222");
            assertThat(event.getAmount()).isEqualByComparingTo("150.75");
        }
    }

    @Test
    @DisplayName("Should expose application, JVM, pool and repository metrics after serving a request")
    void shouldExposeInfrastructureMetricsAfterServingRequest() {
        createActiveAccount("444441");
        createActiveAccount("444442");
        var request = new PaymentRequestDTO("444441", "444442", new BigDecimal("10.00"), "usd", PaymentMethod.PIX);
        restTemplate.postForEntity("/api/v1/payments", request, PaymentResponseDTO.class);

        PrometheusText scrape = scrape();

        assertThat(scrape.sum("payments_publish_seconds_count", "outcome=\"success\"")).isGreaterThanOrEqualTo(1.0);
        assertThat(scrape.sum("payments_accepted_total", "payment_method=\"pix\"")).isGreaterThanOrEqualTo(1.0);
        assertThat(scrape.hasSeries("jvm_memory_used_bytes", "application=\"payment-producer-api\"")).isTrue();
        assertThat(scrape.hasSeries("hikaricp_connections_active")).isTrue();
        assertThat(scrape.hasSeries("spring_data_repository_invocations_seconds_count")).isTrue();
        assertThat(scrape.hasSeries("http_server_requests_seconds_bucket", "le=\"0.5\"", "status=\"202\"")).isTrue();
        assertThat(scrape.hasSeries("http_server_requests_seconds_bucket", "le=\"2.0\"", "status=\"202\"")).isTrue();
    }

    @Test
    @DisplayName("Should count account lookups served from the cache")
    void shouldCountAccountLookupsServedFromTheCache() {
        createActiveAccount("555551");
        restTemplate.getForEntity("/api/v1/accounts/555551", String.class);
        double hitsBefore = scrape().sum("cache_gets_total", "cache=\"accounts\"", "result=\"hit\"");

        restTemplate.getForEntity("/api/v1/accounts/555551", String.class);

        double hitsAfter = scrape().sum("cache_gets_total", "cache=\"accounts\"", "result=\"hit\"");
        assertThat(hitsAfter - hitsBefore).isEqualTo(1.0);
        assertThat(scrape().hasSeries("cache_gets_total", "cache=\"accounts\"", "result=\"miss\"")).isTrue();
    }

    @Test
    @DisplayName("Should report the application as healthy")
    void shouldReportApplicationAsHealthy() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should keep actuator requests out of the HTTP metrics")
    void shouldKeepActuatorRequestsOutOfHttpMetrics() {
        restTemplate.getForEntity("/actuator/health", String.class);

        PrometheusText scrape = scrape();

        assertThat(scrape.hasSeries("http_server_requests_seconds_count", "uri=\"/actuator/health\"")).isFalse();
        assertThat(scrape.hasSeries("http_server_requests_seconds_count", "uri=\"/actuator/prometheus\"")).isFalse();
    }

    private PrometheusText scrape() {
        return new PrometheusText(restTemplate.getForObject("/actuator/prometheus", String.class));
    }

    @Test
    @DisplayName("Should return 404 and never publish when the source account does not exist")
    void shouldReturn404AndNeverPublishWhenSourceAccountDoesNotExist() {
        createActiveAccount("333333");
        var request = new PaymentRequestDTO("000000", "333333", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        ResponseEntity<String> httpResponse = restTemplate.postForEntity("/api/v1/payments", request, String.class);

        assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

}
