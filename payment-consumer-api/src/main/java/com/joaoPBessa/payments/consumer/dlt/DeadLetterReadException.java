package com.joaoPBessa.payments.consumer.dlt;

public class DeadLetterReadException extends RuntimeException {

	public DeadLetterReadException(String message, Throwable cause) {
		super(message, cause);
	}

}
