package com.joaoPBessa.payments.consumer.dlt;

import java.util.List;

import org.springframework.stereotype.Service;

import com.joaoPBessa.payments.consumer.api.dto.response.DeadLetterMessageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeadLetterService {

	private final DeadLetterReader reader;
	private final DeadLetterPayloadDecoder decoder;

	public List<DeadLetterMessageResponse> oldestMessages() {
		return reader.oldest().stream()
				.map(record -> DeadLetterMessageResponse.of(record, decoder.decode(record.value())))
				.toList();
	}

}
