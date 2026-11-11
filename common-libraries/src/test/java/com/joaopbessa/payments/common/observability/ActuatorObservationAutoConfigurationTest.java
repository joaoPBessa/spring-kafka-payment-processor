package com.joaopbessa.payments.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.micrometer.observation.ObservationPredicate;

@DisplayName("ActuatorObservationAutoConfiguration")
class ActuatorObservationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActuatorObservationAutoConfiguration.class));

    @Test
    @DisplayName("Should register the predicate that keeps actuator requests out of the observations")
    void shouldRegisterThePredicateThatKeepsActuatorRequestsOutOfTheObservations() {
        runner.run(context -> assertThat(context).hasSingleBean(ObservationPredicate.class)
                .getBean(ObservationPredicate.class).isInstanceOf(ActuatorObservationPredicate.class));
    }

    @Test
    @DisplayName("Should be discovered by Spring Boot without any import in the applications")
    void shouldBeDiscoveredBySpringBootWithoutAnyImportInTheApplications() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(ActuatorObservationAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("Should back off when the application defines its own predicate")
    void shouldBackOffWhenTheApplicationDefinesItsOwnPredicate() {
        ActuatorObservationPredicate custom = new ActuatorObservationPredicate();

        runner.withBean("customPredicate", ActuatorObservationPredicate.class, () -> custom)
                .run(context -> assertThat(context.getBeansOfType(ActuatorObservationPredicate.class))
                        .containsOnlyKeys("customPredicate"));
    }

    @Test
    @DisplayName("Should stay out of the way when Micrometer is not on the classpath")
    void shouldStayOutOfTheWayWhenMicrometerIsNotOnTheClasspath() {
        runner.withClassLoader(new FilteredClassLoader(ObservationPredicate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ActuatorObservationPredicate.class);
                });
    }

}
