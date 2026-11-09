package com.joaoPBessa.payments.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Flyway configuration")
class FlywayProfileTest {

    private static final String FLYWAY_ENABLED = "spring.flyway.enabled";
    private static final String VAULT_ENABLED = "spring.cloud.vault.enabled";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    @DisplayName("Should leave migrations to the deployment step when running normally")
    void shouldLeaveMigrationsToTheDeploymentStepWhenRunningNormally() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty(FLYWAY_ENABLED, Boolean.class))
                .isFalse());
    }

    @Test
    @DisplayName("Should migrate the database in the test profile")
    void shouldMigrateTheDatabaseInTheTestProfile() {
        runner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context.getEnvironment().getProperty(FLYWAY_ENABLED, Boolean.class))
                        .isTrue());
    }

    @Test
    @DisplayName("Should not look for Vault in the test profile")
    void shouldNotLookForVaultInTheTestProfile() {
        runner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context.getEnvironment().getProperty(VAULT_ENABLED, Boolean.class))
                        .isFalse());
    }

}
