package com.joaoPBessa.payments.producer.api.dto.request;

import java.math.BigDecimal;

import com.joaopBessa.payments.common.domain.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PaymentRequestDTO(
		@NotBlank(message = "Source account is required")
	    String sourceAccount,

	    @NotBlank(message = "Target account is required")
	    String targetAccount,

	    @NotNull(message = "Amount is required")
	    @Positive(message = "Amount must be greater than zero")
	    BigDecimal amount,

	    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO 4217 code")
	    @NotBlank(message = "Currency is required (ISO code)")
	    String currency,

	    @NotNull
	    PaymentMethod paymentMethod
) {
}
