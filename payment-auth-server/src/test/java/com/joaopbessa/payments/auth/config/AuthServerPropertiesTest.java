package com.joaopbessa.payments.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("AuthServerProperties")
class AuthServerPropertiesTest {

    private static final String PROMETHEUS_SECRET = "prometheus-secret-value";
    private static final String TRAFFIC_SECRET = "traffic-secret-value";

    private static final String[] VALID_PROPERTIES = {
            "app.auth.issuer=http://localhost:9000",
            "app.auth.access-token-time-to-live=15m",
            "app.auth.clients.payments-cli.redirect-uri=http://127.0.0.1:8085/callback",
            "app.auth.clients.prometheus.secret=" + PROMETHEUS_SECRET,
            "app.auth.clients.prometheus.roles=METRICS_READ",
            "app.auth.clients.traffic-generator.secret=" + TRAFFIC_SECRET,
            "app.auth.clients.traffic-generator.roles=ACCOUNT_READ,ACCOUNT_WRITE,PAYMENT_CREATE"
    };

    @Configuration
    @EnableConfigurationProperties(AuthServerProperties.class)
    static class PropertiesConfiguration {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    private static String[] without(String prefix) {
        return Arrays.stream(VALID_PROPERTIES).filter(property -> !property.startsWith(prefix))
                .toArray(String[]::new);
    }

    @Test
    @DisplayName("Should bind the issuer, the token lifetime and the three clients")
    void shouldBindIssuerLifetimeAndClients() {
        runner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            AuthServerProperties properties = context.getBean(AuthServerProperties.class);

            assertThat(properties.issuer()).isEqualTo("http://localhost:9000");
            assertThat(properties.accessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.clients().paymentsCli().redirectUri()).isEqualTo("http://127.0.0.1:8085/callback");
            assertThat(properties.clients().prometheus().secret()).isEqualTo(PROMETHEUS_SECRET);
            assertThat(properties.clients().prometheus().roles()).containsExactly("METRICS_READ");
            assertThat(properties.clients().trafficGenerator().roles())
                    .containsExactlyInAnyOrder("ACCOUNT_READ", "ACCOUNT_WRITE", "PAYMENT_CREATE");
        });
    }

    @Test
    @DisplayName("Should fail when a service client has no secret, as happens when Vault was not read")
    void shouldFailWhenAServiceClientHasNoSecret() {
        runner.withPropertyValues(without("app.auth.clients.prometheus.secret"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should fail when a service client has a blank secret")
    void shouldFailWhenAServiceClientHasABlankSecret() {
        runner.withPropertyValues(VALID_PROPERTIES).withPropertyValues("app.auth.clients.prometheus.secret= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should fail when a service client has no roles")
    void shouldFailWhenAServiceClientHasNoRoles() {
        runner.withPropertyValues(without("app.auth.clients.traffic-generator.roles"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should fail when the token lifetime is missing")
    void shouldFailWhenTheTokenLifetimeIsMissing() {
        runner.withPropertyValues(without("app.auth.access-token-time-to-live"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should fail when the issuer is blank")
    void shouldFailWhenTheIssuerIsBlank() {
        runner.withPropertyValues(VALID_PROPERTIES).withPropertyValues("app.auth.issuer= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should never print a client secret, in the properties or in a service client")
    void shouldNeverPrintAClientSecret() {
        runner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            AuthServerProperties properties = context.getBean(AuthServerProperties.class);

            assertThat(properties.toString()).doesNotContain(PROMETHEUS_SECRET, TRAFFIC_SECRET);
            assertThat(properties.clients().prometheus().toString()).doesNotContain(PROMETHEUS_SECRET);
        });
    }

}
