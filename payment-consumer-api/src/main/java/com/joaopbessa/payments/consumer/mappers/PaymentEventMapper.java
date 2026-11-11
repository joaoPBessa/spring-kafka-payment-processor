package com.joaopbessa.payments.consumer.mappers;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.joaopbessa.payments.consumer.domain.entities.Payment;
import com.joaopbessa.payments.common.avro.PaymentEvent;
import com.joaopbessa.payments.common.domain.PaymentMethod;

@Component
public class PaymentEventMapper {

	public Payment toEntity(PaymentEvent event) {
		return Payment.builder()
				.id(UUID.randomUUID())
				.transactionId(event.getTransactionId())
				.sourceAccount(event.getSourceAccount())
				.targetAccount(event.getTargetAccount())
				.amount(event.getAmount())
				.currency(event.getCurrency())
				.paymentMethod(PaymentMethod.fromAvro(event.getPaymentMethod()))
				.eventTimestamp(event.getEventTimestamp())
				.build();
	}

}
