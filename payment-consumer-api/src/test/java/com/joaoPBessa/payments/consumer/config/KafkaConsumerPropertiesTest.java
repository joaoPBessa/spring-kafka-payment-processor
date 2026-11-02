package com.joaoPBessa.payments.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("KafkaConsumerProperties")
class KafkaConsumerPropertiesTest {

    private static final String[] VALID_PROPERTIES = {
            "app.kafka.topics.payment-events=payment.events",
            "app.kafka.topics.payment-events-dlt=payment.events.DLT",
            "app.kafka.retry.max-attempts=3",
            "app.kafka.retry.initial-interval=50ms",
            "app.kafka.retry.multiplier=1.5",
            "app.kafka.retry.max-interval=200ms"
    };

    @Configuration
    @EnableConfigurationProperties(KafkaConsumerProperties.class)
    static class PropertiesConfiguration {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    @DisplayName("Should bind topics and retry settings")
    void shouldBindTopicsAndRetrySettings() {
        runner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            KafkaConsumerProperties properties = context.getBean(KafkaConsumerProperties.class);

            assertThat(properties.topics().paymentEvents()).isEqualTo("payment.events");
            assertThat(properties.topics().paymentEventsDlt()).isEqualTo("payment.events.DLT");
            assertThat(properties.retry().maxAttempts()).isEqualTo(3);
            assertThat(properties.retry().initialInterval()).isEqualTo(Duration.ofMillis(50));
            assertThat(properties.retry().multiplier()).isEqualTo(1.5);
            assertThat(properties.retry().maxInterval()).isEqualTo(Duration.ofMillis(200));
        });
    }

    @Test
    @DisplayName("Should fail when the retry settings are missing")
    void shouldFailWhenRetrySettingsAreMissing() {
        runner.withPropertyValues(
                "app.kafka.topics.payment-events=payment.events",
                "app.kafka.topics.payment-events-dlt=payment.events.DLT")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should fail when a topic name is blank")
    void shouldFailWhenTopicNameIsBlank() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.kafka.topics.payment-events-dlt= ")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("topics.paymentEventsDlt"));
    }

    @Test
    @DisplayName("Should fail when max attempts is lower than one")
    void shouldFailWhenMaxAttemptsIsLowerThanOne() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.kafka.retry.max-attempts=0")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("retry.maxAttempts"));
    }

    @Test
    @DisplayName("Should fail when the multiplier is lower than one")
    void shouldFailWhenMultiplierIsLowerThanOne() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.kafka.retry.multiplier=0.5")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("retry.multiplier"));
    }

    @Test
    @DisplayName("Should fail when the initial interval exceeds the max interval")
    void shouldFailWhenInitialIntervalExceedsMaxInterval() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.kafka.retry.initial-interval=1s")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("retry.intervalOrderValid"));
    }

}
