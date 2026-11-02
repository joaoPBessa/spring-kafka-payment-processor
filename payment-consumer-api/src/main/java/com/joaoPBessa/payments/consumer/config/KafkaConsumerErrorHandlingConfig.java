package com.joaoPBessa.payments.consumer.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import com.joaopBessa.payments.common.avro.PaymentEvent;

import io.apicurio.registry.serde.avro.AvroKafkaSerializer;

@Configuration
@EnableConfigurationProperties(KafkaConsumerProperties.class)
public class KafkaConsumerErrorHandlingConfig {

	private static final int ANY_PARTITION = -1;

	@Bean
	public ProducerFactory<String, PaymentEvent> paymentEventDltProducerFactory(
			KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
		return new DefaultKafkaProducerFactory<>(
				dltProducerProperties(kafkaProperties, connectionDetails, AvroKafkaSerializer.class));
	}

	@Bean
	public ProducerFactory<String, byte[]> rawDltProducerFactory(
			KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
		return new DefaultKafkaProducerFactory<>(
				dltProducerProperties(kafkaProperties, connectionDetails, ByteArraySerializer.class));
	}

	@Bean
	public KafkaTemplate<String, PaymentEvent> paymentEventDltKafkaTemplate(
			ProducerFactory<String, PaymentEvent> paymentEventDltProducerFactory) {
		return new KafkaTemplate<>(paymentEventDltProducerFactory);
	}

	@Bean
	public KafkaTemplate<String, byte[]> rawDltKafkaTemplate(
			ProducerFactory<String, byte[]> rawDltProducerFactory) {
		return new KafkaTemplate<>(rawDltProducerFactory);
	}

	@Bean
	public NewTopic paymentEventsDltTopic(KafkaConsumerProperties properties) {
		return TopicBuilder.name(properties.topics().paymentEventsDlt()).build();
	}

	@Bean
	public DeadLetterPublishingRecoverer paymentEventRecoverer(
			KafkaTemplate<String, PaymentEvent> paymentEventDltKafkaTemplate,
			KafkaTemplate<String, byte[]> rawDltKafkaTemplate,
			KafkaConsumerProperties properties) {
		Map<Class<?>, KafkaOperations<?, ?>> templatesByValueType = new LinkedHashMap<>();
		templatesByValueType.put(PaymentEvent.class, paymentEventDltKafkaTemplate);
		templatesByValueType.put(byte[].class, rawDltKafkaTemplate);
		return new DeadLetterPublishingRecoverer(templatesByValueType,
				dltDestinationResolver(properties.topics().paymentEventsDlt()));
	}

	@Bean
	public DefaultErrorHandler paymentEventErrorHandler(
			DeadLetterPublishingRecoverer paymentEventRecoverer, KafkaConsumerProperties properties) {
		DefaultErrorHandler errorHandler =
				new DefaultErrorHandler(paymentEventRecoverer, retryBackOff(properties.retry()));
		errorHandler.addNotRetryableExceptions(DataIntegrityViolationException.class);
		return errorHandler;
	}

	static ExponentialBackOffWithMaxRetries retryBackOff(KafkaConsumerProperties.Retry retry) {
		ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.maxAttempts() - 1);
		backOff.setInitialInterval(retry.initialInterval().toMillis());
		backOff.setMultiplier(retry.multiplier());
		backOff.setMaxInterval(retry.maxInterval().toMillis());
		return backOff;
	}

	static BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> dltDestinationResolver(String dltTopic) {
		return (record, exception) -> new TopicPartition(dltTopic, ANY_PARTITION);
	}

	private Map<String, Object> dltProducerProperties(KafkaProperties kafkaProperties,
			KafkaConnectionDetails connectionDetails, Class<?> valueSerializer) {
		Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
		producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
				connectionDetails.getProducer().getBootstrapServers());
		producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
		return producerProperties;
	}

}
