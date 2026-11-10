package com.joaoPBessa.payments.consumer.api.dto.response;

import java.util.List;

import com.joaoPBessa.payments.consumer.dlt.ReplayResult;

public record DeadLetterReplayResponse(List<DeadLetterReplayResultResponse> results) {

	public static DeadLetterReplayResponse from(List<ReplayResult> results) {
		return new DeadLetterReplayResponse(results.stream().map(DeadLetterReplayResultResponse::from).toList());
	}

}
