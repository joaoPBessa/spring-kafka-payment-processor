package com.joaopbessa.payments.consumer.config;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.joaopbessa.payments.consumer.dlt.DeadLetterPayloadDecoder;
import com.joaopbessa.payments.consumer.dlt.DeadLetterReader;
import com.joaopbessa.payments.common.avro.PaymentEvent;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;

@Configuration
public class DeadLetterViewConfig {

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	@Bean
	public AvroKafkaDeserializer<PaymentEvent> paymentEventDltDeserializer(KafkaProperties kafkaProperties) {
		AvroKafkaDeserializer<PaymentEvent> deserializer = new AvroKafkaDeserializer<>();
		deserializer.configure(kafkaProperties.buildConsumerProperties(), false);
		return deserializer;
	}

	@Bean
	public DeadLetterPayloadDecoder deadLetterPayloadDecoder(
			Deserializer<PaymentEvent> paymentEventDltDeserializer, KafkaConsumerProperties properties) {
		return new DeadLetterPayloadDecoder(paymentEventDltDeserializer, properties.topics().paymentEventsDlt());
	}

	@Bean
	public DeadLetterReader deadLetterReader(
			KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails,
			KafkaConsumerProperties properties) {
		Map<String, Object> readerProperties = readerProperties(kafkaProperties, connectionDetails);
		Supplier<Consumer<String, byte[]>> consumers = () -> new KafkaConsumer<>(readerProperties);
		return new DeadLetterReader(consumers, properties.topics().paymentEventsDlt(), READ_TIMEOUT);
	}

	private static Map<String, Object> readerProperties(
			KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
		Map<String, Object> readerProperties = kafkaProperties.buildConsumerProperties();
		readerProperties.keySet().removeIf(DeadLetterViewConfig::isDeserializerSetting);
		readerProperties.remove(ConsumerConfig.GROUP_ID_CONFIG);
		readerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
				connectionDetails.getConsumer().getBootstrapServers());
		readerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		readerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
		readerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		readerProperties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
		return readerProperties;
	}

	private static boolean isDeserializerSetting(String name) {
		return name.startsWith("apicurio.") || name.startsWith("spring.deserializer.");
	}

}
