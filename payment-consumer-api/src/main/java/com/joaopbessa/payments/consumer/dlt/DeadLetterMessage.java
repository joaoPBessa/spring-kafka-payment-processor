package com.joaopbessa.payments.consumer.dlt;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public record DeadLetterMessage(int partition, long offset, String key, Instant timestamp, DeadLetterPayload payload) {

	public static DeadLetterMessage of(ConsumerRecord<String, byte[]> record, DeadLetterPayload payload) {
		return new DeadLetterMessage(
				record.partition(), record.offset(), record.key(), Instant.ofEpochMilli(record.timestamp()), payload);
	}

}
