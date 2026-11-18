package com.joaopbessa.payments.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Resource server configuration")
class ResourceServerConfigTest {

    private static final String ISSUER_URI = "spring.security.oauth2.resourceserver.jwt.issuer-uri";
    private static final String JWK_SET_URI = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    @DisplayName("Should read the signing keys from the JWKS endpoint of the issuer, without OIDC discovery")
    void shouldReadTheSigningKeysFromTheJwksEndpointOfTheIssuer() {
        // Without jwk-set-uri Spring Security discovers the keys through /.well-known/openid-configuration, which
        // the authorization server does not serve: it answers with its login page and every token fails with a 500.
        runner.run(context -> {
            assertThat(context.getEnvironment().getProperty(ISSUER_URI)).isEqualTo("http://localhost:9000");
            assertThat(context.getEnvironment().getProperty(JWK_SET_URI)).isEqualTo("http://localhost:9000/oauth2/jwks");
        });
    }

}
