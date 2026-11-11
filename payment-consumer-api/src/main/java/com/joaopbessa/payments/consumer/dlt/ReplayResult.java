package com.joaopbessa.payments.consumer.dlt;

public record ReplayResult(String transactionId, ReplayOutcome outcome, Integer partition, Long offset) {

	public static ReplayResult replayed(String transactionId, int partition, long offset) {
		return new ReplayResult(transactionId, ReplayOutcome.REPLAYED, partition, offset);
	}

	public static ReplayResult notFound(String transactionId) {
		return new ReplayResult(transactionId, ReplayOutcome.NOT_FOUND, null, null);
	}

	public static ReplayResult failed(String transactionId) {
		return new ReplayResult(transactionId, ReplayOutcome.FAILED, null, null);
	}

}
