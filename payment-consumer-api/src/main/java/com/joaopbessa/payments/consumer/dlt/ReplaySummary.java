package com.joaopbessa.payments.consumer.dlt;

public record ReplaySummary(int replayed, int failed) {

	public static ReplaySummary empty() {
		return new ReplaySummary(0, 0);
	}

}
