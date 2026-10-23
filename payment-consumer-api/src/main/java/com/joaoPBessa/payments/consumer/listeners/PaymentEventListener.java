package com.joaoPBessa.payments.consumer.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.joaoPBessa.payments.consumer.services.PaymentPersistenceService;
import com.joaopBessa.payments.common.avro.PaymentEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

	private static final String PAYMENT_EVENTS_TOPIC = "${app.kafka.topics.payment-events}";

	private final PaymentPersistenceService paymentPersistenceService;

	@KafkaListener(topics = PAYMENT_EVENTS_TOPIC)
	public void onPaymentEvent(PaymentEvent event) {
		paymentPersistenceService.persist(event);
	}

}
