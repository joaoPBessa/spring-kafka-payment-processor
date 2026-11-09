package com.joaoPBessa.payments.consumer.api.dto.response;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.joaoPBessa.payments.consumer.dlt.DeadLetterPayload;

public record DeadLetterMessageResponse(
		int partition,
		long offset,
		String key,
		Instant timestamp,
		PaymentEventResponse payload,
		String rawPayload
) {

	public static DeadLetterMessageResponse of(ConsumerRecord<String, byte[]> record, DeadLetterPayload payload) {
		return new DeadLetterMessageResponse(
				record.partition(),
				record.offset(),
				record.key(),
				Instant.ofEpochMilli(record.timestamp()),
				payload.event() == null ? null : PaymentEventResponse.from(payload.event()),
				payload.rawPayload());
	}

}
