package com.joaoPBessa.payments.producer.api.dto.request;

import java.util.UUID;

import com.joaoPBessa.payments.producer.domain.entities.Account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequestDTO(
		@NotBlank(message = "Account number is required")
	    @Size(min = 4, max = 8, message = "Account number must be between {min} and {max} characters")
	    @Pattern(regexp = "\\d+", message = "Account number must contain only numeric digits")
	    String accountNumber,

	    @NotBlank(message = "Account name is required")
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
