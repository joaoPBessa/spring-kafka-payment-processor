package com.joaoPBessa.payments.producer.metrics;

public enum PublishOutcome {

	SUCCESS("success"),
	TIMEOUT("timeout"),
	ERROR("error");

	private final String tagValue;

	PublishOutcome(String tagValue) {
		this.tagValue = tagValue;
	}

	public String tagValue() {
		return tagValue;
	}

}
