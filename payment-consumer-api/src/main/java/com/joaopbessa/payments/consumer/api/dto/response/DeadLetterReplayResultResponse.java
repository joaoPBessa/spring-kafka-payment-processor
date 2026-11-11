package com.joaopbessa.payments.consumer.api.dto.response;

import com.joaopbessa.payments.consumer.dlt.ReplayResult;

public record DeadLetterReplayResultResponse(String transactionId, String status, Integer partition, Long offset) {

	public static DeadLetterReplayResultResponse from(ReplayResult result) {
		return new DeadLetterReplayResultResponse(
				result.transactionId(), result.outcome().value(), result.partition(), result.offset());
	}

}
