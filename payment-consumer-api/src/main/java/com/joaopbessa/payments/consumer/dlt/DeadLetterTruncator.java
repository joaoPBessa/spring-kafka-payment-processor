package com.joaopbessa.payments.consumer.dlt;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;

public class DeadLetterTruncator {

	private final Supplier<Admin> adminSupplier;
	private final Duration timeout;

	public DeadLetterTruncator(Supplier<Admin> adminSupplier, Duration timeout) {
		this.adminSupplier = adminSupplier;
		this.timeout = timeout;
	}

	public void truncate(Map<TopicPartition, Long> beforeOffsets) {
		if (beforeOffsets.isEmpty()) {
			return;
		}
		Map<TopicPartition, RecordsToDelete> request = beforeOffsets.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> RecordsToDelete.beforeOffset(entry.getValue())));
		try (Admin admin = adminSupplier.get()) {
			admin.deleteRecords(request).all().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DeadLetterReplayException("Interrupted while truncating the dead letter topic", e);
		} catch (ExecutionException | TimeoutException | KafkaException e) {
			throw new DeadLetterReplayException("Could not truncate the dead letter topic", e);
		}
	}

}
