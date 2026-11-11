package com.joaopbessa.payments.consumer.dlt;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeadLetterService {

	private final DeadLetterReader reader;
	private final DeadLetterPayloadDecoder decoder;

	public List<DeadLetterMessage> oldestMessages() {
		return reader.oldest().stream()
				.map(record -> DeadLetterMessage.of(record, decoder.decode(record.value())))
				.toList();
	}

}
