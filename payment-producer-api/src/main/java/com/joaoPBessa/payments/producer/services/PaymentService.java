package com.joaoPBessa.payments.producer.services;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaoPBessa.payments.producer.config.KafkaProducerProperties;
import com.joaoPBessa.payments.producer.exceptions.PaymentPublishException;
import com.joaoPBessa.payments.producer.metrics.PaymentMetrics;
import com.joaoPBessa.payments.producer.metrics.PublishOutcome;
import com.joaopBessa.payments.common.avro.PaymentEvent;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final KafkaTemplate<String, PaymentEvent> paymentEventKafkaTemplate;
	private final KafkaProducerProperties kafkaProducerProperties;
	private final AccountService accountService;
	private final PaymentMetrics paymentMetrics;

	public String publishPayment(PaymentRequestDTO request) {
		validateAccountsExist(request);

		String transactionId = UUID.randomUUID().toString();
		PaymentEvent event = buildEvent(transactionId, request);
		send(event);
		paymentMetrics.paymentAccepted(request.paymentMethod(), event.getAmount());
		return transactionId;
	}

	private void validateAccountsExist(PaymentRequestDTO request) {
		accountService.findByNumber(request.sourceAccount());
		accountService.findByNumber(request.targetAccount());
	}

	private void send(PaymentEvent event) {
		String topic = kafkaProducerProperties.topics().paymentEvents();
		var timeout = kafkaProducerProperties.producer().sendTimeout();

		Timer.Sample sample = paymentMetrics.startPublish();
		try {
			paymentEventKafkaTemplate.send(topic, event.getTransactionId(), event)
					.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			paymentMetrics.stopPublish(sample, PublishOutcome.SUCCESS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			paymentMetrics.stopPublish(sample, PublishOutcome.ERROR);
			throw new PaymentPublishException("Interrupted while publishing payment event to Kafka", e);
		} catch (TimeoutException e) {
			paymentMetrics.stopPublish(sample, PublishOutcome.TIMEOUT);
			throw new PaymentPublishException("Failed to publish payment event to Kafka within " + timeout, e);
		} catch (ExecutionException | RuntimeException e) {
			paymentMetrics.stopPublish(sample, PublishOutcome.ERROR);
			throw new PaymentPublishException("Failed to publish payment event to Kafka", e);
		}
	}

	private PaymentEvent buildEvent(String transactionId, PaymentRequestDTO request) {
		return PaymentEvent.newBuilder()
				.setTransactionId(transactionId)
				.setEventTimestamp(Instant.now())
				.setSourceAccount(request.sourceAccount())
				.setTargetAccount(request.targetAccount())
				.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP))
				.setCurrency(request.currency().toUpperCase())
				.setPaymentMethod(request.paymentMethod().toAvroPaymentMethod())
				.build();
	}

}
