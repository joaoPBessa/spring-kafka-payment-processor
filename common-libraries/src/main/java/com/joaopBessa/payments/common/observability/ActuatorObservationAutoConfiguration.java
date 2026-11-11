package com.joaopBessa.payments.common.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;

@AutoConfiguration
@ConditionalOnClass({ ObservationPredicate.class, ServerRequestObservationContext.class })
public class ActuatorObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActuatorObservationPredicate.class)
    public ObservationPredicate actuatorObservationPredicate() {
        return new ActuatorObservationPredicate();
    }

}
