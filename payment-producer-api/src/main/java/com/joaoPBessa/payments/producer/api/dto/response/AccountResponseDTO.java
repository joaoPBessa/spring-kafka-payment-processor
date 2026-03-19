package com.joaoPBessa.payments.producer.api.dto.response;

import java.time.LocalDateTime;

import com.joaoPBessa.payments.producer.domain.entities.Account;

public record AccountResponseDTO(
		String number,
		String name,
		boolean active,
		LocalDateTime createdAt
) {
	
	public static AccountResponseDTO fromEntity(Account entity) {
		return new AccountResponseDTO(
				entity.getNumber(),
				entity.getName(),
				entity.getActive(),
				entity.getCreatedAt());
	}
	
}
