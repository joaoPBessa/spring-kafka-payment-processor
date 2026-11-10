package com.joaoPBessa.payments.consumer.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app.dlt-replay")
public record DeadLetterReplayProperties(
		boolean enabled,
		@NotBlank String cron,
		@NotNull ZoneId zone,
		@Min(1) int maxBatchSize,
		@NotNull Duration sendTimeout) {

	@AssertTrue(message = "cron must be a valid Spring cron expression")
	public boolean isCronValid() {
		return cron == null || cron.isBlank() || CronExpression.isValidExpression(cron);
	}

}
