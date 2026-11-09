package com.joaoPBessa.payments.consumer.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.domain.PaymentMethod;

public record PaymentEventResponse(
		String transactionId,
		Instant eventTimestamp,
		String sourceAccount,
		String targetAccount,
		BigDecimal amount,
		String currency,
		PaymentMethod paymentMethod
) {

	public static PaymentEventResponse from(PaymentEvent event) {
		return new PaymentEventResponse(
				event.getTransactionId(),
				event.getEventTimestamp(),
				event.getSourceAccount(),
				event.getTargetAccount(),
				event.getAmount(),
				event.getCurrency(),
				PaymentMethod.fromAvro(event.getPaymentMethod()));
	}

}
