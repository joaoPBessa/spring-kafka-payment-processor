package com.joaopbessa.payments.consumer.dlt;

public enum ReplayOutcome {

	REPLAYED("replayed"),
	NOT_FOUND("not_found"),
	FAILED("failed");

	private final String value;

	ReplayOutcome(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

}
