package com.joaoPBessa.payments.producer.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
		
		@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		LocalDateTime timestamp,
		
		int status,
		
		String message,
		
		@JsonProperty("errors")
		List<FieldErrorDetails> errors
) {
	
	public record FieldErrorDetails (String field, String message) {}
	
}
