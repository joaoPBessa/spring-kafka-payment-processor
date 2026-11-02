package com.joaoPBessa.payments.consumer.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaConsumerProperties(@NotNull @Valid Topics topics, @NotNull @Valid Retry retry) {

	public record Topics(@NotBlank String paymentEvents, @NotBlank String paymentEventsDlt) {
	}

	public record Retry(@Min(1) int maxAttempts,
			@NotNull Duration initialInterval,
			@DecimalMin("1.0") double multiplier,
			@NotNull Duration maxInterval) {

		@AssertTrue(message = "initial-interval must not exceed max-interval")
		public boolean isIntervalOrderValid() {
			return initialInterval == null || maxInterval == null || initialInterval.compareTo(maxInterval) <= 0;
		}
	}

}
