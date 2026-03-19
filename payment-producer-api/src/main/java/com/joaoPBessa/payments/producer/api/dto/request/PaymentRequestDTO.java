package com.joaoPBessa.payments.producer.api.dto.request;

import java.math.BigDecimal;

import com.joaopBessa.payments.common.domain.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentRequestDTO(
		@NotBlank(message = "Source account is required")
	    String sourceAccount,

	    @NotBlank(message = "Target account is required")
	    String targetAccount,

	    @NotNull(message = "Amount is required")
	    @Positive(message = "Amount must be greater than zero")
	    BigDecimal amount,

	    @Size(min = 3, max = 3)
	    @NotBlank(message = "Currency is required (ISO code)")
	    String currency,

	    @NotNull
	    PaymentMethod paymentMethod
) {
}
