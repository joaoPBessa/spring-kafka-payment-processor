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
import com.joaopBessa.payments.common.avro.PaymentEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final KafkaTemplate<String, PaymentEvent> paymentEventKafkaTemplate;
	private final KafkaProducerProperties kafkaProducerProperties;
	private final AccountService accountService;

	public String publishPayment(PaymentRequestDTO request) {
		validateAccountsExist(request);

		String transactionId = UUID.randomUUID().toString();
		PaymentEvent event = buildEvent(transactionId, request);
		send(event);
		return transactionId;
	}

	private void validateAccountsExist(PaymentRequestDTO request) {
		accountService.findByNumber(request.sourceAccount());
		accountService.findByNumber(request.targetAccount());
	}

	private void send(PaymentEvent event) {
		String topic = kafkaProducerProperties.topics().paymentEvents();
		var timeout = kafkaProducerProperties.producer().sendTimeout();

		try {
			paymentEventKafkaTemplate.send(topic, event.getSourceAccount(), event)
					.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PaymentPublishException("Interrupted while publishing payment event to Kafka", e);
		} catch (ExecutionException | TimeoutException e) {
			throw new PaymentPublishException("Failed to publish payment event to Kafka within " + timeout, e);
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
