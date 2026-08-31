package com.joaoPBessa.payments.producer.config;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.joaopBessa.payments.common.avro.PaymentEvent;

@Configuration
@EnableConfigurationProperties(KafkaProducerProperties.class)
public class KafkaProducerConfig {

	@Bean
	public ProducerFactory<String, PaymentEvent> paymentEventProducerFactory(KafkaProperties kafkaProperties) {
		Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
		return new DefaultKafkaProducerFactory<>(producerProperties);
	}

	@Bean
	public KafkaTemplate<String, PaymentEvent> paymentEventKafkaTemplate(
			ProducerFactory<String, PaymentEvent> paymentEventProducerFactory) {
		return new KafkaTemplate<>(paymentEventProducerFactory);
	}

}
