package com.joaoPBessa.payments.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import com.joaoPBessa.payments.consumer.config.KafkaConsumerProperties.Retry;
import com.joaoPBessa.payments.consumer.config.KafkaConsumerProperties.Topics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("KafkaConsumerErrorHandlingConfig")
class KafkaConsumerErrorHandlingConfigTest {

    private static final String EVENTS_TOPIC = "payment.events";
    private static final String DLT_TOPIC = "payment.events.DLT";
    private static final int MAX_ATTEMPTS = 4;

    private final KafkaConsumerProperties properties = new KafkaConsumerProperties(
            new Topics(EVENTS_TOPIC, DLT_TOPIC),
            new Retry(MAX_ATTEMPTS, Duration.ofMillis(100), 2.0, Duration.ofSeconds(5)));

    private final KafkaConsumerErrorHandlingConfig config = new KafkaConsumerErrorHandlingConfig();

    @Test
    @DisplayName("Should retry one time less than the configured number of attempts")
    void shouldRetryOneTimeLessThanConfiguredAttempts() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerErrorHandlingConfig.retryBackOff(properties.retry());

        assertThat(backOff.getMaxRetries()).isEqualTo(MAX_ATTEMPTS - 1);
    }

    @Test
    @DisplayName("Should apply the configured intervals and multiplier to the backoff")
    void shouldApplyConfiguredIntervalsAndMultiplier() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerErrorHandlingConfig.retryBackOff(properties.retry());

        assertThat(backOff.getInitialInterval()).isEqualTo(100L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
        assertThat(backOff.getMaxInterval()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should send every failed record to the dead letter topic without pinning a partition")
    void shouldSendEveryFailedRecordToDeadLetterTopicWithoutPinningPartition() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(EVENTS_TOPIC, 3, 42L, "key", "value");

        TopicPartition destination = KafkaConsumerErrorHandlingConfig.dltDestinationResolver(DLT_TOPIC)
                .apply(record, new IllegalStateException("boom"));

        assertThat(destination).isEqualTo(new TopicPartition(DLT_TOPIC, -1));
    }

    private KafkaConnectionDetails connectionDetails() {
        KafkaConnectionDetails connectionDetails = mock(KafkaConnectionDetails.class);
        when(connectionDetails.getProducer())
                .thenReturn(KafkaConnectionDetails.Configuration.of(List.of("localhost:9092")));
        return connectionDetails;
    }

    private void assertMetered(ProducerFactory<?, ?> factory) {
        assertThat(factory.getListeners()).hasAtLeastOneElementOfType(MicrometerProducerListener.class);
    }

    @Test
    @DisplayName("Should export Kafka client metrics for the payment event dead letter producer")
    void shouldExportKafkaClientMetricsForPaymentEventDeadLetterProducer() {
        assertMetered(config.paymentEventDltProducerFactory(new KafkaProperties(), connectionDetails(),
                new SimpleMeterRegistry()));
    }

    @Test
    @DisplayName("Should export Kafka client metrics for the raw dead letter producer")
    void shouldExportKafkaClientMetricsForRawDeadLetterProducer() {
        assertMetered(config.rawDltProducerFactory(new KafkaProperties(), connectionDetails(),
                new SimpleMeterRegistry()));
    }

    @Test
    @DisplayName("Should declare the dead letter topic with the configured name")
    void shouldDeclareDeadLetterTopicWithConfiguredName() {
        NewTopic topic = config.paymentEventsDltTopic(properties);

        assertThat(topic.name()).isEqualTo(DLT_TOPIC);
    }

}
