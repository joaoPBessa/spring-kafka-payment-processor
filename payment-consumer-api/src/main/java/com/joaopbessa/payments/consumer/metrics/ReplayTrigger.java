package com.joaopbessa.payments.consumer.metrics;

public enum ReplayTrigger {

	SCHEDULED("scheduled"),
	MANUAL("manual");

	private final String tagValue;

	ReplayTrigger(String tagValue) {
		this.tagValue = tagValue;
	}

	public String tagValue() {
		return tagValue;
	}

}
