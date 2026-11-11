package com.joaopbessa.payments.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("json-logs profile")
class JsonLogsProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=json-logs");

    @Test
    @DisplayName("Should switch the console to the ECS JSON format")
    void shouldSwitchConsoleToEcsJsonFormat() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty("logging.structured.format.console"))
                .isEqualTo("ecs"));
    }

    @Test
    @DisplayName("Should keep plain text logs when the profile is not active")
    void shouldKeepPlainTextLogsWhenProfileIsNotActive() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context.getEnvironment().getProperty("logging.structured.format.console"))
                        .isNull());
    }

}
