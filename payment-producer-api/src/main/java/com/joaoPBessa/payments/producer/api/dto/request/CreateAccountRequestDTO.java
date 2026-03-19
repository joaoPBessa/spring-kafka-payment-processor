package com.joaoPBessa.payments.producer.api.dto.request;

import java.util.UUID;

import com.joaoPBessa.payments.producer.domain.entities.Account;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequestDTO(
		@NotBlank(message = "Account Numer is required")
		String accountNumber,
		@NotBlank(message = "Acocunt Name is required")
		String accountName
) {
	
	public Account toEntity() {
		return Account.builder()
				.id(UUID.randomUUID())
				.number(this.accountNumber)
				.name(this.accountName)
				.active(Boolean.TRUE)
				.build();
	}
	
}
