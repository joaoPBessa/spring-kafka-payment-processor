package com.joaopbessa.payments.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("DeadLetterReplayProperties")
class DeadLetterReplayPropertiesTest {

    private static final String[] VALID_PROPERTIES = {
            "app.dlt-replay.enabled=true",
            "app.dlt-replay.cron=0 0 4 * * *",
            "app.dlt-replay.zone=America/Sao_Paulo",
            "app.dlt-replay.max-batch-size=200",
            "app.dlt-replay.send-timeout=3s"
    };

    @Configuration
    @EnableConfigurationProperties(DeadLetterReplayProperties.class)
    static class PropertiesConfiguration {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    @DisplayName("Should bind the schedule and the batch settings")
    void shouldBindTheScheduleAndTheBatchSettings() {
        runner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            DeadLetterReplayProperties properties = context.getBean(DeadLetterReplayProperties.class);

            assertThat(properties.enabled()).isTrue();
            assertThat(properties.cron()).isEqualTo("0 0 4 * * *");
            assertThat(properties.zone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
            assertThat(properties.maxBatchSize()).isEqualTo(200);
            assertThat(properties.sendTimeout()).isEqualTo(Duration.ofSeconds(3));
        });
    }

    @Test
    @DisplayName("Should default to a daily run at 03:00 UTC with the packaged configuration")
    void shouldDefaultToADailyRunAtThreeUtcWithThePackagedConfiguration() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer()).run(context -> {
            DeadLetterReplayProperties properties = context.getBean(DeadLetterReplayProperties.class);

            assertThat(properties.enabled()).isTrue();
            assertThat(properties.cron()).isEqualTo("0 0 3 * * *");
            assertThat(properties.zone()).isEqualTo(ZoneId.of("UTC"));
            assertThat(properties.maxBatchSize()).isEqualTo(500);
            assertThat(properties.sendTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    @DisplayName("Should fail when the cron expression is not valid")
    void shouldFailWhenTheCronExpressionIsNotValid() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.dlt-replay.cron=every day")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("cronValid"));
    }

    @Test
    @DisplayName("Should fail when the cron expression is blank")
    void shouldFailWhenTheCronExpressionIsBlank() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.dlt-replay.cron= ")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("cron"));
    }

    @Test
    @DisplayName("Should fail when the batch size is lower than one")
    void shouldFailWhenTheBatchSizeIsLowerThanOne() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.dlt-replay.max-batch-size=0")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("maxBatchSize"));
    }

    @Test
    @DisplayName("Should fail when the time zone is unknown")
    void shouldFailWhenTheTimeZoneIsUnknown() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.dlt-replay.zone=Mars/Olympus")
                .run(context -> assertThat(context).hasFailed());
    }

}
