package com.joaoPBessa.payments.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.joaopBessa.payments.common.observability.ActuatorObservationPredicate;

import io.micrometer.observation.ObservationPredicate;

@Configuration
public class ObservabilityConfig {

	@Bean
	public ObservationPredicate actuatorObservationPredicate() {
		return new ActuatorObservationPredicate();
	}

}
