package com.joaopbessa.payments.consumer.dlt;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

import com.joaopbessa.payments.consumer.metrics.ReplayTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DeadLetterReplayService {

	private static final BinaryOperator<ConsumerRecord<String, byte[]>> MOST_RECENT = BinaryOperator.maxBy(Comparator
			.<ConsumerRecord<String, byte[]>>comparingLong(ConsumerRecord::timestamp)
			.thenComparingInt(ConsumerRecord::partition)
			.thenComparingLong(ConsumerRecord::offset));

	private final DeadLetterReader reader;
	private final DeadLetterReplayer replayer;
	private final DeadLetterTruncator truncator;
	private final int maxBatchSize;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public ReplaySummary replayPending() {
		if (!running.compareAndSet(false, true)) {
			log.warn("A dead letter replay is already running, skipping this run");
			return ReplaySummary.empty();
		}
		try {
			return replayBacklog();
		} finally {
			running.set(false);
		}
	}

	public List<ReplayResult> replay(List<String> transactionIds) {
		Map<String, ConsumerRecord<String, byte[]>> latestByKey = latestByKey(reader.all());
		return transactionIds.stream()
				.distinct()
				.map(transactionId -> replayOne(transactionId, latestByKey.get(transactionId)))
				.toList();
	}

	private ReplaySummary replayBacklog() {
		Map<TopicPartition, Long> replayedUpTo = new HashMap<>();
		Set<Integer> stoppedPartitions = new HashSet<>();
		int replayed = 0;
		int failed = 0;
		for (ConsumerRecord<String, byte[]> deadLetter : reader.pending(maxBatchSize)) {
			if (stoppedPartitions.contains(deadLetter.partition())) {
				continue;
			}
			try {
				replayer.replay(deadLetter, ReplayTrigger.SCHEDULED);
				replayedUpTo.put(new TopicPartition(deadLetter.topic(), deadLetter.partition()), deadLetter.offset() + 1);
				replayed++;
			} catch (DeadLetterReplayException e) {
				log.warn("Stopping the replay of partition {} at offset {}", deadLetter.partition(), deadLetter.offset(), e);
				stoppedPartitions.add(deadLetter.partition());
				failed++;
			}
		}
		truncator.truncate(replayedUpTo);
		log.info("Dead letter replay finished: {} replayed, {} failed", replayed, failed);
		return new ReplaySummary(replayed, failed);
	}

	private ReplayResult replayOne(String transactionId, ConsumerRecord<String, byte[]> deadLetter) {
		if (deadLetter == null) {
			return ReplayResult.notFound(transactionId);
		}
		try {
			replayer.replay(deadLetter, ReplayTrigger.MANUAL);
			return ReplayResult.replayed(transactionId, deadLetter.partition(), deadLetter.offset());
		} catch (DeadLetterReplayException e) {
			log.warn("Could not replay the dead letter of transaction {}", transactionId, e);
			return ReplayResult.failed(transactionId);
		}
	}

	private static Map<String, ConsumerRecord<String, byte[]>> latestByKey(List<ConsumerRecord<String, byte[]>> records) {
		return records.stream()
				.filter(deadLetter -> deadLetter.key() != null)
				.collect(Collectors.toMap(ConsumerRecord::key, Function.identity(), MOST_RECENT));
	}

}
