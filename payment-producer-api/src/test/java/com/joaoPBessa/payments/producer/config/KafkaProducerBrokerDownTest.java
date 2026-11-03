package com.joaoPBessa.payments.producer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import com.joaopBessa.payments.common.avro.PaymentEvent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("Kafka producer with the broker down")
class KafkaProducerBrokerDownTest {

    private static final Duration FAIL_FAST_LIMIT = Duration.ofSeconds(20);
    private static final String UNREACHABLE_BROKER = "localhost:1";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(KafkaProducerConfig.class)
            .withPropertyValues("spring.kafka.bootstrap-servers=" + UNREACHABLE_BROKER);

    private PaymentEvent event() {
        return PaymentEvent.newBuilder()
                .setTransactionId("11111111-1111-1111-1111-111111111111")
                .setEventTimestamp(Instant.now())
                .setSourceAccount("123456")
                .setTargetAccount("654321")
                .setAmount(new BigDecimal("10.00"))
                .setCurrency("USD")
                .setPaymentMethod(com.joaopBessa.payments.common.avro.PaymentMethod.PIX)
                .build();
    }

    @Test
    @DisplayName("Should give up sending within seconds instead of the default minute")
    void shouldGiveUpSendingWithinSecondsInsteadOfTheDefaultMinute() {
        runner.run(context -> {
            KafkaTemplate<String, PaymentEvent> template = context.getBean(KafkaTemplate.class);

            assertTimeoutPreemptively(FAIL_FAST_LIMIT, () -> assertThatThrownBy(
                    () -> template.send("payment.events", "123456", event()).get())
                    .satisfiesAnyOf(
                            failure -> assertThat(failure).isInstanceOf(org.apache.kafka.common.KafkaException.class),
                            failure -> assertThat(failure).hasRootCauseInstanceOf(
                                    org.apache.kafka.common.errors.TimeoutException.class)));
        });
    }

}
