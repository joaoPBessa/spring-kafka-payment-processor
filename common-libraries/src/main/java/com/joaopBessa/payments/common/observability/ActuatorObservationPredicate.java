package com.joaopBessa.payments.common.observability;

import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

public class ActuatorObservationPredicate implements ObservationPredicate {

    private static final String ACTUATOR_PATH_PREFIX = "/actuator";

    @Override
    public boolean test(String name, Observation.Context context) {
        if (context instanceof ServerRequestObservationContext serverContext) {
            return !serverContext.getCarrier().getRequestURI().startsWith(ACTUATOR_PATH_PREFIX);
        }
        return true;
    }

}
