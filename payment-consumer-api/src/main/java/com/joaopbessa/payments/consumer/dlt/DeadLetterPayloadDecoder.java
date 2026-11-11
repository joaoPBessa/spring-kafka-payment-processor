package com.joaopbessa.payments.consumer.dlt;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.kafka.common.serialization.Deserializer;

import com.joaopbessa.payments.common.avro.PaymentEvent;

public class DeadLetterPayloadDecoder {

	private final Deserializer<PaymentEvent> eventDeserializer;
	private final String topic;

	public DeadLetterPayloadDecoder(Deserializer<PaymentEvent> eventDeserializer, String topic) {
		this.eventDeserializer = eventDeserializer;
		this.topic = topic;
	}

	public DeadLetterPayload decode(byte[] value) {
		if (value == null) {
			return DeadLetterPayload.undecodable(null);
		}
		try {
			return DeadLetterPayload.decoded(eventDeserializer.deserialize(topic, value));
		} catch (RuntimeException e) {
			return DeadLetterPayload.undecodable(printable(value));
		}
	}

	private static String printable(byte[] value) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(value))
					.toString();
		} catch (CharacterCodingException e) {
			return Base64.getEncoder().encodeToString(value);
		}
	}

}
