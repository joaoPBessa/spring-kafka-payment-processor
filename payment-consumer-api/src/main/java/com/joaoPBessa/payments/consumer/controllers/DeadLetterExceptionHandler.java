package com.joaoPBessa.payments.consumer.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.joaoPBessa.payments.consumer.dlt.DeadLetterReadException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class DeadLetterExceptionHandler {

	private static final String UNAVAILABLE_DETAIL = "The dead letter topic is unavailable, try again later";

	@ExceptionHandler(DeadLetterReadException.class)
	public ProblemDetail handleDeadLetterReadFailure(DeadLetterReadException exception) {
		log.error("Failed to read the dead letter topic", exception);
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_DETAIL);
	}

}
