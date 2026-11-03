package com.joaoPBessa.payments.consumer.metrics;

public enum DeadLetterKind {

	EVENT("event"),
	POISON_PILL("poison_pill");

	private final String tagValue;

	DeadLetterKind(String tagValue) {
		this.tagValue = tagValue;
	}

	public String tagValue() {
		return tagValue;
	}

}
