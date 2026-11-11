package com.joaopbessa.payments.consumer.metrics;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryMetricsListener")
class RetryMetricsListenerTest {

    @Mock
    private PaymentConsumerMetrics metrics;

    @InjectMocks
    private RetryMetricsListener listener;

    private final ConsumerRecord<String, String> record = new ConsumerRecord<>("payment.events", 0, 7L, "key", "value");
    private final Exception failure = new IllegalStateException("database unavailable");
    private final Exception publishFailure = new IllegalStateException("dlt unavailable");

    @Test
    @DisplayName("Should count every failed delivery")
    void shouldCountEveryFailedDelivery() {
        listener.failedDelivery(record, failure, 1);
        listener.failedDelivery(record, failure, 2);

        verify(metrics, times(2)).deliveryFailed();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    @DisplayName("Should count a recovered record as dead-lettered")
    void shouldCountRecoveredRecordAsDeadLettered() {
        listener.recovered(record, failure);

        verify(metrics, times(1)).deadLettered(failure);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    @DisplayName("Should count a failed recovery against the original failure")
    void shouldCountFailedRecoveryAgainstOriginalFailure() {
        listener.recoveryFailed(record, failure, publishFailure);

        verify(metrics, times(1)).deadLetterFailed(failure);
        verifyNoMoreInteractions(metrics);
    }

}
