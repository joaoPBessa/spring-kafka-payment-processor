package com.joaoPBessa.payments.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.PaymentResponseDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;
import com.joaoPBessa.payments.producer.repositories.AccountRepository;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.domain.PaymentMethod;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("Payment publishing (Testcontainers integration)")
class PaymentPublishingIntegrationTest {

    @Container
    static final GenericContainer<?> APICURIO =
            new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.3.2")).withExposedPorts(8080);

    @DynamicPropertySource
    static void apicurioProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.producer.properties.apicurio.registry.url",
                () -> "http://%s:%d/apis/registry/v3".formatted(APICURIO.getHost(), APICURIO.getMappedPort(8080)));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

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
        consumerProps.put("apicurio.registry.url",
                "http://%s:%d/apis/registry/v3".formatted(APICURIO.getHost(), APICURIO.getMappedPort(8080)));
        consumerProps.put("apicurio.registry.use-specific-avro-reader", "true");

        try (KafkaConsumer<String, PaymentEvent> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("payment.events"));
            ConsumerRecords<String, PaymentEvent> records = consumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isEqualTo(1);
            PaymentEvent event = records.iterator().next().value();
            assertThat(event.getTransactionId()).isEqualTo(transactionId);
            assertThat(event.getSourceAccount()).isEqualTo("111111");
            assertThat(event.getTargetAccount()).isEqualTo("222222");
            assertThat(event.getAmount()).isEqualByComparingTo("150.75");
        }
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
