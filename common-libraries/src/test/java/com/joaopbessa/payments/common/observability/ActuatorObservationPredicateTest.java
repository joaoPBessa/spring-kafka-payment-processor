package com.joaopbessa.payments.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.observation.Observation;

@DisplayName("ActuatorObservationPredicate")
class ActuatorObservationPredicateTest {

    private static final String SERVER_OBSERVATION = "http.server.requests";

    private final ActuatorObservationPredicate predicate = new ActuatorObservationPredicate();

    private ServerRequestObservationContext contextFor(String uri) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", uri), new MockHttpServletResponse());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/actuator", "/actuator/health", "/actuator/prometheus" })
    @DisplayName("Should ignore requests to actuator endpoints")
    void shouldIgnoreRequestsToActuatorEndpoints(String uri) {
        assertThat(predicate.test(SERVER_OBSERVATION, contextFor(uri))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/payments", "/api/v1/accounts/123", "/" })
    @DisplayName("Should keep requests to business endpoints")
    void shouldKeepRequestsToBusinessEndpoints(String uri) {
        assertThat(predicate.test(SERVER_OBSERVATION, contextFor(uri))).isTrue();
    }

    @Test
    @DisplayName("Should keep observations that are not server requests")
    void shouldKeepObservationsThatAreNotServerRequests() {
        assertThat(predicate.test("spring.kafka.listener", new Observation.Context())).isTrue();
    }

}
