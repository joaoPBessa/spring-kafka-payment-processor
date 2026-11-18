package com.joaopbessa.payments.producer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.joaopbessa.payments.common.security.TestJwtIssuer;

/**
 * Lets a test authenticate without the authorization server: the application validates the tokens that
 * {@link TestJwtIssuer} signs, instead of fetching keys from the real issuer.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfiguration {

	@Bean
	TestJwtIssuer testJwtIssuer() {
		return new TestJwtIssuer();
	}

	@Bean
	JwtDecoder jwtDecoder(TestJwtIssuer testJwtIssuer) {
		return testJwtIssuer.decoder();
	}

}
