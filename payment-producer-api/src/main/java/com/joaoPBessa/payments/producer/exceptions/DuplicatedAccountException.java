package com.joaoPBessa.payments.producer.exceptions;

public class DuplicatedAccountException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public DuplicatedAccountException(String message) {
		super(message);
	}

}
