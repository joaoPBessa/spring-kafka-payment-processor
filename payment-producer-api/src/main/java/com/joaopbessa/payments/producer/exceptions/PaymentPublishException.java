package com.joaopbessa.payments.producer.exceptions;

public class PaymentPublishException extends RuntimeException {

	public PaymentPublishException(String message, Throwable cause) {
		super(message, cause);
	}

}
