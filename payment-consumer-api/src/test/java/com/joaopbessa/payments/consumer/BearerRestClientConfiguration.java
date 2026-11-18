package com.joaopbessa.payments.consumer;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.joaopbessa.payments.common.security.TestJwtIssuer;

/**
 * For the integration tests that call the running application: the token issuer of {@link TestJwtConfiguration} and
 * a {@link BearerRestClient} on top of the test REST template.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestJwtConfiguration.class)
public class BearerRestClientConfiguration {

	@Bean
	BearerRestClient bearerRestClient(TestRestTemplate restTemplate, TestJwtIssuer issuer) {
		return new BearerRestClient(restTemplate, issuer);
	}

}
