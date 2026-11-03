package com.joaoPBessa.payments.producer.metrics;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.joaopBessa.payments.common.domain.PaymentMethod;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class PaymentMetrics {

	public static final String PUBLISH_TIMER = "payments.publish";
	public static final String ACCEPTED_COUNTER = "payments.accepted";
	public static final String AMOUNT_SUMMARY = "payments.amount";
	public static final String OUTCOME_TAG = "outcome";
	public static final String PAYMENT_METHOD_TAG = "payment_method";

	private final MeterRegistry registry;
	private final Map<PublishOutcome, Timer> publishTimers = new EnumMap<>(PublishOutcome.class);
	private final Map<PaymentMethod, Counter> acceptedCounters = new EnumMap<>(PaymentMethod.class);
	private final DistributionSummary amountSummary;

	public PaymentMetrics(MeterRegistry registry) {
		this.registry = registry;
		for (PublishOutcome outcome : PublishOutcome.values()) {
			publishTimers.put(outcome, Timer.builder(PUBLISH_TIMER)
					.description("Time to publish a payment event and get the broker confirmation")
					.tag(OUTCOME_TAG, outcome.tagValue())
					.publishPercentileHistogram()
					.register(registry));
		}
		for (PaymentMethod method : PaymentMethod.values()) {
			acceptedCounters.put(method, Counter.builder(ACCEPTED_COUNTER)
					.description("Payments accepted and published to Kafka")
					.tag(PAYMENT_METHOD_TAG, method.getValue())
					.register(registry));
		}
		this.amountSummary = DistributionSummary.builder(AMOUNT_SUMMARY)
				.description("Amount of the payments accepted")
				.register(registry);
	}

	public Timer.Sample startPublish() {
		return Timer.start(registry);
	}

	public void stopPublish(Timer.Sample sample, PublishOutcome outcome) {
		sample.stop(publishTimers.get(outcome));
	}

	public void paymentAccepted(PaymentMethod method, BigDecimal amount) {
		acceptedCounters.get(method).increment();
		amountSummary.record(amount.doubleValue());
	}

}
