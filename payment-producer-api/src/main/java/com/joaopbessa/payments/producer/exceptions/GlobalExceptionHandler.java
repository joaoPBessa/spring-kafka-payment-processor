package com.joaopbessa.payments.producer.exceptions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.joaopbessa.payments.producer.api.dto.response.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldErrorDetails> errorList = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldErrorDetails(
                        error.getField(), 
                        error.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed: Multiple errors found",
                errorList
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
	
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMalformedRequestException(HttpMessageNotReadableException ex) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Malformed JSON request",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

	@ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse mvcError && mvcError.getStatusCode().is4xxClientError()) {
            return handleClientErrorOfSpringMvc(mvcError);
        }
        log.error("Unhandled exception while processing request", ex);
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

	/**
	 * Spring MVC raises its own exceptions for the requests it cannot route or read (no such path, a method or media
	 * type the endpoint does not support, a missing parameter), and each one already carries the status it means
	 * (404, 405, 415, 400) and the headers it needs (such as {@code Allow}). They are the client's mistake, not an
	 * unexpected failure, so they keep that status and are not logged as errors.
	 */
	private ResponseEntity<ErrorResponse> handleClientErrorOfSpringMvc(org.springframework.web.ErrorResponse mvcError) {
        ProblemDetail problem = mvcError.getBody();
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                problem.getStatus(),
                problem.getDetail() != null ? problem.getDetail() : problem.getTitle(),
                null
        );
        return ResponseEntity.status(mvcError.getStatusCode()).headers(mvcError.getHeaders()).body(response);
    }

	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFoundException(Exception ex) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
	
	@ExceptionHandler(DuplicatedAccountException.class)
	public ResponseEntity<ErrorResponse> handleDuplicatedResourceException(Exception ex) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

	@ExceptionHandler(PaymentPublishException.class)
	public ResponseEntity<ErrorResponse> handlePaymentPublishException(PaymentPublishException ex) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Payment could not be published to Kafka: " + ex.getMessage(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

}
