package com.joaoPBessa.payments.consumer.metrics;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetryMetricsListener implements RetryListener {

	private final PaymentConsumerMetrics metrics;

	@Override
	public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
		metrics.deliveryFailed();
	}

	@Override
	public void recovered(ConsumerRecord<?, ?> record, Exception exception) {
		metrics.deadLettered(exception);
	}

	@Override
	public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
		metrics.deadLetterFailed(original);
	}

}
