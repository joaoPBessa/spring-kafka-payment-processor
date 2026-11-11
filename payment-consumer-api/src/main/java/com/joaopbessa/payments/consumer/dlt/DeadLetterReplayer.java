package com.joaopbessa.payments.consumer.dlt;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;

import com.joaopbessa.payments.consumer.metrics.PaymentConsumerMetrics;
import com.joaopbessa.payments.consumer.metrics.ReplayTrigger;

public class DeadLetterReplayer {

	private static final String DEAD_LETTER_HEADER_PREFIX = "kafka_dlt-";

	private final KafkaTemplate<String, byte[]> template;
	private final String eventsTopic;
	private final Duration sendTimeout;
	private final PaymentConsumerMetrics metrics;

	public DeadLetterReplayer(KafkaTemplate<String, byte[]> template, String eventsTopic, Duration sendTimeout,
			PaymentConsumerMetrics metrics) {
		this.template = template;
		this.eventsTopic = eventsTopic;
		this.sendTimeout = sendTimeout;
		this.metrics = metrics;
	}

	public void replay(ConsumerRecord<String, byte[]> deadLetter, ReplayTrigger trigger) {
		try {
			send(deadLetter);
			metrics.deadLetterReplayed(trigger);
		} catch (DeadLetterReplayException e) {
			metrics.deadLetterReplayFailed(trigger);
			throw e;
		}
	}

	private void send(ConsumerRecord<String, byte[]> deadLetter) {
		ProducerRecord<String, byte[]> record = new ProducerRecord<>(
				eventsTopic, null, deadLetter.key(), deadLetter.value(), replayHeaders(deadLetter));
		try {
			template.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DeadLetterReplayException("Interrupted while replaying " + describe(deadLetter), e);
		} catch (ExecutionException | TimeoutException | RuntimeException e) {
			throw new DeadLetterReplayException("Could not replay " + describe(deadLetter), e);
		}
	}

	private static List<Header> replayHeaders(ConsumerRecord<String, byte[]> deadLetter) {
		return StreamSupport.stream(deadLetter.headers().spliterator(), false)
				.filter(header -> !header.key().startsWith(DEAD_LETTER_HEADER_PREFIX))
				.toList();
	}

	private static String describe(ConsumerRecord<String, byte[]> deadLetter) {
		return "dead letter " + deadLetter.partition() + "-" + deadLetter.offset();
	}

}
