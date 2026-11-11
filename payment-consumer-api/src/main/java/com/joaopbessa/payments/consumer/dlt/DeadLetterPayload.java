package com.joaopbessa.payments.consumer.dlt;

import com.joaopbessa.payments.common.avro.PaymentEvent;

public record DeadLetterPayload(PaymentEvent event, String rawPayload) {

	public static DeadLetterPayload decoded(PaymentEvent event) {
		return new DeadLetterPayload(event, null);
	}

	public static DeadLetterPayload undecodable(String rawPayload) {
		return new DeadLetterPayload(null, rawPayload);
	}

}
