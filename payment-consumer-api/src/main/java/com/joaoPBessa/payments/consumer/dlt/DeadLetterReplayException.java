package com.joaoPBessa.payments.consumer.dlt;

public class DeadLetterReplayException extends RuntimeException {

	public DeadLetterReplayException(String message, Throwable cause) {
		super(message, cause);
	}

}
