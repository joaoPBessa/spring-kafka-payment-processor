package com.joaoPBessa.payments.producer.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccountRequestDTO(
		@NotBlank(message = "The account name must be provided")
		String accountName
) {
}
