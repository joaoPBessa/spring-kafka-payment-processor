package com.joaoPBessa.payments.producer.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaProducerProperties(Topics topics, Producer producer) {

	public record Topics(String paymentEvents) {}

	public record Producer(Duration sendTimeout) {}

}
