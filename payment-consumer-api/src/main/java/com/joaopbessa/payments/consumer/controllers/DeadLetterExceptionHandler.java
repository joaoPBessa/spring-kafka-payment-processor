package com.joaopbessa.payments.consumer.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.joaopbessa.payments.consumer.dlt.DeadLetterReadException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class DeadLetterExceptionHandler {

	private static final String UNAVAILABLE_DETAIL = "The dead letter topic is unavailable, try again later";
	private static final String ACCESS_DENIED_DETAIL = "Access denied";

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ACCESS_DENIED_DETAIL);
	}

	@ExceptionHandler(DeadLetterReadException.class)
	public ProblemDetail handleDeadLetterReadFailure(DeadLetterReadException exception) {
		log.error("Failed to read the dead letter topic", exception);
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_DETAIL);
	}

}
