package com.joaoPBessa.payments.consumer.api.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ReplayDeadLettersRequest(
		@NotEmpty(message = "At least one transaction id is required")
		@Size(max = ReplayDeadLettersRequest.MAX_IDS, message = "At most {max} transaction ids can be replayed at once")
		List<@NotBlank(message = "Transaction ids must not be blank") String> transactionIds
) {

	public static final int MAX_IDS = 100;

}
