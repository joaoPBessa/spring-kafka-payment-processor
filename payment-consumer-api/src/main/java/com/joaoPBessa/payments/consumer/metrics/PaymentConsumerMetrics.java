package com.joaoPBessa.payments.consumer.metrics;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class PaymentConsumerMetrics {

	public static final String PERSISTED_COUNTER = "payments.persisted";
	public static final String FAILED_DELIVERIES_COUNTER = "kafka.consumer.failed.deliveries";
	public static final String DEAD_LETTERED_COUNTER = "payments.dead.lettered";
	public static final String DEAD_LETTER_FAILURES_COUNTER = "payments.dead.letter.failures";
	public static final String RESULT_TAG = "result";
	public static final String KIND_TAG = "kind";
	private static final String RESULT_SUCCESS = "success";
	private static final String RESULT_DUPLICATE = "duplicate";

	private final Counter persistedSuccess;
	private final Counter persistedDuplicate;
	private final Counter failedDeliveries;
	private final Map<DeadLetterKind, Counter> deadLettered = new EnumMap<>(DeadLetterKind.class);
	private final Map<DeadLetterKind, Counter> deadLetterFailures = new EnumMap<>(DeadLetterKind.class);

	public PaymentConsumerMetrics(MeterRegistry registry) {
		this.persistedSuccess = persistedCounter(registry, RESULT_SUCCESS);
		this.persistedDuplicate = persistedCounter(registry, RESULT_DUPLICATE);
		this.failedDeliveries = Counter.builder(FAILED_DELIVERIES_COUNTER)
				.description("Failed attempts to process a record, including the ones that were retried")
				.register(registry);
		for (DeadLetterKind kind : DeadLetterKind.values()) {
			deadLettered.put(kind, kindCounter(registry, DEAD_LETTERED_COUNTER,
					"Records published to the dead letter topic", kind));
			deadLetterFailures.put(kind, kindCounter(registry, DEAD_LETTER_FAILURES_COUNTER,
					"Records that could not be published to the dead letter topic", kind));
		}
	}

	public void paymentPersisted() {
		persistedSuccess.increment();
	}

	public void duplicateIgnored() {
		persistedDuplicate.increment();
	}

	public void deliveryFailed() {
		failedDeliveries.increment();
	}

	public void deadLettered(Throwable failure) {
		deadLettered.get(kindOf(failure)).increment();
	}

	public void deadLetterFailed(Throwable failure) {
		deadLetterFailures.get(kindOf(failure)).increment();
	}

	private static DeadLetterKind kindOf(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof DeserializationException) {
				return DeadLetterKind.POISON_PILL;
			}
		}
		return DeadLetterKind.EVENT;
	}

	private static Counter persistedCounter(MeterRegistry registry, String result) {
		return Counter.builder(PERSISTED_COUNTER)
				.description("Payment events processed, by result")
				.tag(RESULT_TAG, result)
				.register(registry);
	}

	private static Counter kindCounter(MeterRegistry registry, String name, String description, DeadLetterKind kind) {
		return Counter.builder(name)
				.description(description)
				.tag(KIND_TAG, kind.tagValue())
				.register(registry);
	}

}
