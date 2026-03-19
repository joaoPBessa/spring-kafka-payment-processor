package com.joaoPBessa.payments.producer.api.dto.response;

import java.math.BigDecimal;

public record PaymentResponseDTO(
		String transactionCode,
		String sourceAccount,
		String targetAccount,
		BigDecimal amount
) {}
