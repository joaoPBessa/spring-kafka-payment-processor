package com.joaopbessa.payments.consumer.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("PaymentConsumerMetrics")
class PaymentConsumerMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PaymentConsumerMetrics metrics = new PaymentConsumerMetrics(registry);

    private double persisted(String result) {
        return registry.get(PaymentConsumerMetrics.PERSISTED_COUNTER)
                .tag(PaymentConsumerMetrics.RESULT_TAG, result).counter().count();
    }

    private double deadLettered(DeadLetterKind kind) {
        return registry.get(PaymentConsumerMetrics.DEAD_LETTERED_COUNTER)
                .tag(PaymentConsumerMetrics.KIND_TAG, kind.tagValue()).counter().count();
    }

    private double deadLetterFailures(DeadLetterKind kind) {
        return registry.get(PaymentConsumerMetrics.DEAD_LETTER_FAILURES_COUNTER)
                .tag(PaymentConsumerMetrics.KIND_TAG, kind.tagValue()).counter().count();
    }

    private double failedDeliveries() {
        return registry.get(PaymentConsumerMetrics.FAILED_DELIVERIES_COUNTER).counter().count();
    }

    private double replays(ReplayTrigger trigger, String result) {
        return registry.get(PaymentConsumerMetrics.REPLAYED_COUNTER)
                .tag(PaymentConsumerMetrics.TRIGGER_TAG, trigger.tagValue())
                .tag(PaymentConsumerMetrics.RESULT_TAG, result).counter().count();
    }

    private Exception eventFailure() {
        return new ListenerExecutionFailedException("listener failed", new DataIntegrityViolationException("fk"));
    }

    private Exception poisonPillFailure() {
        return new DeserializationException("cannot deserialize", new byte[0], false, new IllegalStateException("bad"));
    }

    @Test
    @DisplayName("Should register every counter at zero so rates see the first event")
    void shouldRegisterEveryCounterAtZero() {
        assertThat(persisted("success")).isZero();
        assertThat(persisted("duplicate")).isZero();
        assertThat(failedDeliveries()).isZero();
        for (DeadLetterKind kind : DeadLetterKind.values()) {
            assertThat(deadLettered(kind)).isZero();
            assertThat(deadLetterFailures(kind)).isZero();
        }
    }

    @Test
    @DisplayName("Should count a persisted payment apart from an ignored duplicate")
    void shouldCountPersistedPaymentApartFromIgnoredDuplicate() {
        metrics.paymentPersisted();
        metrics.paymentPersisted();
        metrics.duplicateIgnored();

        assertThat(persisted("success")).isEqualTo(2.0);
        assertThat(persisted("duplicate")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should register the replay counters at zero for every trigger")
    void shouldRegisterTheReplayCountersAtZeroForEveryTrigger() {
        for (ReplayTrigger trigger : ReplayTrigger.values()) {
            assertThat(replays(trigger, "success")).isZero();
            assertThat(replays(trigger, "failure")).isZero();
        }
    }

    @Test
    @DisplayName("Should count replays apart by trigger and result")
    void shouldCountReplaysApartByTriggerAndResult() {
        metrics.deadLetterReplayed(ReplayTrigger.SCHEDULED);
        metrics.deadLetterReplayed(ReplayTrigger.SCHEDULED);
        metrics.deadLetterReplayed(ReplayTrigger.MANUAL);
        metrics.deadLetterReplayFailed(ReplayTrigger.MANUAL);

        assertThat(replays(ReplayTrigger.SCHEDULED, "success")).isEqualTo(2.0);
        assertThat(replays(ReplayTrigger.SCHEDULED, "failure")).isZero();
        assertThat(replays(ReplayTrigger.MANUAL, "success")).isEqualTo(1.0);
        assertThat(replays(ReplayTrigger.MANUAL, "failure")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should count failed deliveries")
    void shouldCountFailedDeliveries() {
        metrics.deliveryFailed();
        metrics.deliveryFailed();

        assertThat(failedDeliveries()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should classify a dead-lettered listener failure as an event")
    void shouldClassifyDeadLetteredListenerFailureAsEvent() {
        metrics.deadLettered(eventFailure());

        assertThat(deadLettered(DeadLetterKind.EVENT)).isEqualTo(1.0);
        assertThat(deadLettered(DeadLetterKind.POISON_PILL)).isZero();
    }

    @Test
    @DisplayName("Should classify a dead-lettered deserialization failure as a poison pill")
    void shouldClassifyDeadLetteredDeserializationFailureAsPoisonPill() {
        metrics.deadLettered(poisonPillFailure());

        assertThat(deadLettered(DeadLetterKind.POISON_PILL)).isEqualTo(1.0);
        assertThat(deadLettered(DeadLetterKind.EVENT)).isZero();
    }

    @Test
    @DisplayName("Should find the deserialization failure behind a wrapping exception")
    void shouldFindDeserializationFailureBehindWrappingException() {
        metrics.deadLettered(new ListenerExecutionFailedException("wrapped", poisonPillFailure()));

        assertThat(deadLettered(DeadLetterKind.POISON_PILL)).isEqualTo(1.0);
    }

    @ParameterizedTest
    @EnumSource(DeadLetterKind.class)
    @DisplayName("Should count a failed dead letter publication by kind")
    void shouldCountFailedDeadLetterPublicationByKind(DeadLetterKind kind) {
        Exception cause = kind == DeadLetterKind.POISON_PILL ? poisonPillFailure() : eventFailure();

        metrics.deadLetterFailed(cause);

        assertThat(deadLetterFailures(kind)).isEqualTo(1.0);
        assertThat(deadLettered(kind)).isZero();
    }

}
