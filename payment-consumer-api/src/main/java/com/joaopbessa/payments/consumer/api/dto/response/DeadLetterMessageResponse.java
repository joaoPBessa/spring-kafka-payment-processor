package com.joaopbessa.payments.consumer.api.dto.response;

import java.time.Instant;

import com.joaopbessa.payments.consumer.dlt.DeadLetterMessage;

public record DeadLetterMessageResponse(
		int partition,
		long offset,
		String key,
		Instant timestamp,
		PaymentEventResponse payload,
		String rawPayload
) {

	public static DeadLetterMessageResponse from(DeadLetterMessage message) {
		return new DeadLetterMessageResponse(
				message.partition(),
				message.offset(),
				message.key(),
				message.timestamp(),
				message.payload().event() == null ? null : PaymentEventResponse.from(message.payload().event()),
				message.payload().rawPayload());
	}

}
