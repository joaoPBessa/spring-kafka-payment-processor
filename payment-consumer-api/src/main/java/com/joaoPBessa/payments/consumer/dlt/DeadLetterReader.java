package com.joaoPBessa.payments.consumer.dlt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

public class DeadLetterReader {

	public static final int MAX_MESSAGES = 10;

	private static final Duration POLL_INTERVAL = Duration.ofMillis(300);
	private static final Comparator<ConsumerRecord<String, byte[]>> OLDEST_FIRST = Comparator
			.<ConsumerRecord<String, byte[]>>comparingLong(ConsumerRecord::timestamp)
			.thenComparingInt(ConsumerRecord::partition)
			.thenComparingLong(ConsumerRecord::offset);

	private final Supplier<Consumer<String, byte[]>> consumerSupplier;
	private final String topic;
	private final Duration readTimeout;

	public DeadLetterReader(Supplier<Consumer<String, byte[]>> consumerSupplier, String topic, Duration readTimeout) {
		this.consumerSupplier = consumerSupplier;
		this.topic = topic;
		this.readTimeout = readTimeout;
	}

	public List<ConsumerRecord<String, byte[]>> oldest() {
		try (Consumer<String, byte[]> consumer = consumerSupplier.get()) {
			List<TopicPartition> partitions = partitionsOf(consumer);
			if (partitions.isEmpty()) {
				return List.of();
			}
			return readOldest(consumer, partitions);
		} catch (KafkaException e) {
			throw new DeadLetterReadException("Could not read the dead letter topic " + topic, e);
		}
	}

	private List<TopicPartition> partitionsOf(Consumer<String, byte[]> consumer) {
		List<PartitionInfo> infos = consumer.partitionsFor(topic);
		if (infos == null) {
			return List.of();
		}
		return infos.stream().map(info -> new TopicPartition(info.topic(), info.partition())).toList();
	}

	private List<ConsumerRecord<String, byte[]>> readOldest(Consumer<String, byte[]> consumer,
			List<TopicPartition> partitions) {
		consumer.assign(partitions);
		consumer.seekToBeginning(partitions);
		Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
		Map<TopicPartition, Integer> collected = new HashMap<>();
		List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
		long deadline = System.nanoTime() + readTimeout.toNanos();
		while (System.nanoTime() < deadline && hasPending(consumer, partitions, endOffsets, collected)) {
			consumer.poll(POLL_INTERVAL).forEach(record -> {
				records.add(record);
				collected.merge(new TopicPartition(record.topic(), record.partition()), 1, Integer::sum);
			});
		}
		return records.stream().sorted(OLDEST_FIRST).limit(MAX_MESSAGES).toList();
	}

	private boolean hasPending(Consumer<String, byte[]> consumer, Collection<TopicPartition> partitions,
			Map<TopicPartition, Long> endOffsets, Map<TopicPartition, Integer> collected) {
		return partitions.stream().anyMatch(partition -> collected.getOrDefault(partition, 0) < MAX_MESSAGES
				&& consumer.position(partition) < endOffsets.get(partition));
	}

}
