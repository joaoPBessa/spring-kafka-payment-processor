package com.joaoPBessa.payments.consumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.joaopBessa.payments.common.avro.PaymentEvent;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;

public class KafkaTestClients implements AutoCloseable {

	private static final String REGISTRY_URL_KEY = "apicurio.registry.url";
	private static final String AUTO_REGISTER_KEY = "apicurio.registry.auto-register";
	private static final String SPECIFIC_READER_KEY = "apicurio.registry.use-specific-avro-reader";
	private static final Duration POLL_INTERVAL = Duration.ofMillis(300);

	private final List<String> bootstrapServers;
	private final String registryUrl;
	private final String eventsTopic;
	private final String dltTopic;
	private final KafkaProducer<String, PaymentEvent> eventProducer;
	private final KafkaProducer<String, byte[]> rawProducer;
	private final AvroKafkaDeserializer<PaymentEvent> eventDeserializer;

	public KafkaTestClients(List<String> bootstrapServers, String registryUrl, String eventsTopic, String dltTopic) {
		this.bootstrapServers = bootstrapServers;
		this.registryUrl = registryUrl;
		this.eventsTopic = eventsTopic;
		this.dltTopic = dltTopic;
		this.eventProducer = new KafkaProducer<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class,
				REGISTRY_URL_KEY, registryUrl,
				AUTO_REGISTER_KEY, true));
		this.rawProducer = new KafkaProducer<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
		this.eventDeserializer = new AvroKafkaDeserializer<>();
		this.eventDeserializer.configure(Map.of(REGISTRY_URL_KEY, registryUrl, SPECIFIC_READER_KEY, true), false);
	}

	public void sendEvent(String key, PaymentEvent event) {
		send(eventProducer, new ProducerRecord<>(eventsTopic, key, event));
	}

	public void sendRaw(String key, byte[] payload) {
		send(rawProducer, new ProducerRecord<>(eventsTopic, key, payload));
	}

	public List<ConsumerRecord<String, byte[]>> pollDeadLetters(String key, Duration timeout) {
		List<ConsumerRecord<String, byte[]>> matching = new ArrayList<>();
		long deadline = System.nanoTime() + timeout.toNanos();
		try (KafkaConsumer<String, byte[]> consumer = newDltConsumer()) {
			consumer.subscribe(List.of(dltTopic));
			while (System.nanoTime() < deadline) {
				consumer.poll(POLL_INTERVAL).forEach(record -> {
					if (key.equals(record.key())) {
						matching.add(record);
					}
				});
			}
		}
		return matching;
	}

	public PaymentEvent deserialize(byte[] payload) {
		return eventDeserializer.deserialize(dltTopic, payload);
	}

	@Override
	public void close() {
		eventProducer.close();
		rawProducer.close();
		eventDeserializer.close();
	}

	private KafkaConsumer<String, byte[]> newDltConsumer() {
		return new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
	}

	private <V> void send(KafkaProducer<String, V> producer, ProducerRecord<String, V> record) {
		try {
			producer.send(record).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while sending test record", e);
		} catch (java.util.concurrent.ExecutionException e) {
			throw new IllegalStateException("Failed to send test record", e);
		}
	}

}
