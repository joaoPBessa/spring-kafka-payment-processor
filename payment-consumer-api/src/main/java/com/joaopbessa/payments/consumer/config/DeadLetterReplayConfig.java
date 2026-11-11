package com.joaopbessa.payments.consumer.config;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import com.joaopbessa.payments.consumer.dlt.DeadLetterReader;
import com.joaopbessa.payments.consumer.dlt.DeadLetterReplayService;
import com.joaopbessa.payments.consumer.dlt.DeadLetterReplayer;
import com.joaopbessa.payments.consumer.dlt.DeadLetterTruncator;
import com.joaopbessa.payments.consumer.metrics.PaymentConsumerMetrics;

@Configuration
@EnableConfigurationProperties(DeadLetterReplayProperties.class)
public class DeadLetterReplayConfig {

	@Bean
	public DeadLetterReplayer deadLetterReplayer(KafkaTemplate<String, byte[]> rawDltKafkaTemplate,
			KafkaConsumerProperties kafkaProperties, DeadLetterReplayProperties properties,
			PaymentConsumerMetrics metrics) {
		return new DeadLetterReplayer(rawDltKafkaTemplate, kafkaProperties.topics().paymentEvents(),
				properties.sendTimeout(), metrics);
	}

	@Bean
	public DeadLetterReplayService deadLetterReplayService(DeadLetterReader reader, DeadLetterReplayer replayer,
			DeadLetterTruncator truncator, DeadLetterReplayProperties properties) {
		return new DeadLetterReplayService(reader, replayer, truncator, properties.maxBatchSize());
	}

	@Bean
	public DeadLetterTruncator deadLetterTruncator(KafkaAdmin kafkaAdmin, DeadLetterReplayProperties properties) {
		return new DeadLetterTruncator(() -> Admin.create(kafkaAdmin.getConfigurationProperties()),
				properties.sendTimeout());
	}

}
