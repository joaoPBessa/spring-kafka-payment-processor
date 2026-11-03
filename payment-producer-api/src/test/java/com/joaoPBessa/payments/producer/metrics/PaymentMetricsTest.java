package com.joaoPBessa.payments.producer.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.joaopBessa.payments.common.domain.PaymentMethod;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("PaymentMetrics")
class PaymentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PaymentMetrics metrics = new PaymentMetrics(registry);

    private Timer publishTimer(PublishOutcome outcome) {
        return registry.find(PaymentMetrics.PUBLISH_TIMER).tag(PaymentMetrics.OUTCOME_TAG, outcome.tagValue()).timer();
    }

    private Counter acceptedCounter(PaymentMethod method) {
        return registry.find(PaymentMetrics.ACCEPTED_COUNTER).tag(PaymentMetrics.PAYMENT_METHOD_TAG, method.getValue())
                .counter();
    }

    @ParameterizedTest
    @EnumSource(PublishOutcome.class)
    @DisplayName("Should register every publish outcome at zero so rates see the first event")
    void shouldRegisterEveryPublishOutcomeAtZero(PublishOutcome outcome) {
        assertThat(publishTimer(outcome)).isNotNull();
        assertThat(publishTimer(outcome).count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    @DisplayName("Should register the accepted counter of every payment method at zero")
    void shouldRegisterAcceptedCounterOfEveryPaymentMethodAtZero(PaymentMethod method) {
        assertThat(acceptedCounter(method)).isNotNull();
        assertThat(acceptedCounter(method).count()).isZero();
    }

    @Test
    @DisplayName("Should record the publish duration only under the given outcome")
    void shouldRecordPublishDurationOnlyUnderGivenOutcome() {
        Timer.Sample sample = metrics.startPublish();

        metrics.stopPublish(sample, PublishOutcome.TIMEOUT);

        assertThat(publishTimer(PublishOutcome.TIMEOUT).count()).isEqualTo(1);
        assertThat(publishTimer(PublishOutcome.SUCCESS).count()).isZero();
        assertThat(publishTimer(PublishOutcome.ERROR).count()).isZero();
    }

    @Test
    @DisplayName("Should count an accepted payment by method and record its amount")
    void shouldCountAcceptedPaymentByMethodAndRecordItsAmount() {
        metrics.paymentAccepted(PaymentMethod.PIX, new BigDecimal("150.75"));

        DistributionSummary amounts = registry.find(PaymentMetrics.AMOUNT_SUMMARY).summary();
        assertThat(acceptedCounter(PaymentMethod.PIX).count()).isEqualTo(1.0);
        assertThat(acceptedCounter(PaymentMethod.CRYPTO).count()).isZero();
        assertThat(amounts.count()).isEqualTo(1);
        assertThat(amounts.totalAmount()).isEqualTo(150.75);
    }

}
