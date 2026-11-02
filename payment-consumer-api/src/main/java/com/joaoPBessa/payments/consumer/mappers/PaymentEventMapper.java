package com.joaoPBessa.payments.consumer.mappers;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.joaoPBessa.payments.consumer.domain.entities.Payment;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.domain.PaymentMethod;

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
