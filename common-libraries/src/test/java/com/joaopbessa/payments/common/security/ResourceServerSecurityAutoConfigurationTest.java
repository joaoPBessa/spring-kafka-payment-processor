package com.joaopbessa.payments.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@DisplayName("ResourceServerSecurityAutoConfiguration")
class ResourceServerSecurityAutoConfigurationTest {

    private static final String REPORTER_ROLE = "REPORTER";

    private static final String BASE_PATH = "${management.endpoints.web.base-path:/actuator}";

    private final TestJwtIssuer issuer = new TestJwtIssuer();

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResourceServerSecurityAutoConfiguration.class))
            .withUserConfiguration(WebConfiguration.class)
            .withBean(JwtDecoder.class, issuer::decoder);

    /** The same application with the Actuator endpoint registry, which is what maps the endpoint ids to paths. */
    private final WebApplicationContextRunner actuatorRunner = runner
            .withConfiguration(AutoConfigurations.of(EndpointAutoConfiguration.class, WebEndpointAutoConfiguration.class))
            .withUserConfiguration(OperationalEndpoints.class)
            .withPropertyValues("management.endpoints.web.exposure.include=health,prometheus");

    private void withMockMvc(MockMvcAssertions assertions) {
        withMockMvc(runner, assertions);
    }

    private void withMockMvc(WebApplicationContextRunner contextRunner, MockMvcAssertions assertions) {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                    .apply(springSecurity()).build();
            assertions.accept(mockMvc);
        });
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** A real decoder whose signing keys cannot be fetched: its JWKS endpoint is a local port that nobody listens on. */
    private WebApplicationContextRunner runnerWithUnreachableSigningKeys() throws IOException {
        String jwkSetUri = "http://127.0.0.1:%d/oauth2/jwks".formatted(closedLocalPort());
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ResourceServerSecurityAutoConfiguration.class))
                .withUserConfiguration(WebConfiguration.class)
                .withBean(JwtDecoder.class, () -> NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build());
    }

    private static int closedLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    @DisplayName("Should answer 401 with a Bearer challenge when the request has no token")
    void shouldAnswer401WithABearerChallengeWhenTheRequestHasNoToken() {
        withMockMvc(mockMvc -> mockMvc.perform(get("/guarded")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer"))));
    }

    @Test
    @DisplayName("Should answer 401 when the token is not a valid JWT")
    void shouldAnswer401WhenTheTokenIsNotAValidJwt() {
        withMockMvc(mockMvc -> mockMvc.perform(bearer(get("/guarded"), "not-a-jwt")).andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("Should answer 401 when the token is expired")
    void shouldAnswer401WhenTheTokenIsExpired() {
        withMockMvc(mockMvc -> mockMvc.perform(bearer(get("/guarded"), issuer.expiredToken(REPORTER_ROLE)))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("Should answer 401 when the token was signed by another issuer")
    void shouldAnswer401WhenTheTokenWasSignedByAnotherIssuer() {
        withMockMvc(mockMvc -> mockMvc.perform(bearer(get("/guarded"), new TestJwtIssuer().token(REPORTER_ROLE)))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("Should answer 503 when the signing keys of the issuer cannot be fetched")
    void shouldAnswer503WhenTheSigningKeysOfTheIssuerCannotBeFetched() throws Exception {
        withMockMvc(runnerWithUnreachableSigningKeys(), mockMvc -> mockMvc
                .perform(bearer(get("/guarded"), issuer.token(REPORTER_ROLE)))
                .andExpect(status().isServiceUnavailable()));
    }

    @Test
    @DisplayName("Should answer 503 to a state-changing request with a Bearer token when the keys cannot be fetched")
    void shouldAnswer503ToAStateChangingRequestWhenTheKeysCannotBeFetched() throws Exception {
        withMockMvc(runnerWithUnreachableSigningKeys(), mockMvc -> mockMvc
                .perform(bearer(post("/guarded"), issuer.token(REPORTER_ROLE)))
                .andExpect(status().isServiceUnavailable()));
    }

    @Test
    @DisplayName("Should keep answering 401 when the keys are unreachable but there is no token, or it is no JWT")
    void shouldKeepAnswering401WhenTheKeysAreUnreachableButThereIsNoUsableToken() throws Exception {
        withMockMvc(runnerWithUnreachableSigningKeys(), mockMvc -> {
            mockMvc.perform(get("/guarded")).andExpect(status().isUnauthorized());
            mockMvc.perform(bearer(get("/guarded"), "not-a-jwt")).andExpect(status().isUnauthorized());
        });
    }

    @Test
    @DisplayName("Should answer 403 when the token lacks the role the endpoint requires")
    void shouldAnswer403WhenTheTokenLacksTheRoleTheEndpointRequires() {
        withMockMvc(mockMvc -> mockMvc.perform(bearer(get("/guarded"), issuer.token("SOMETHING_ELSE")))
                .andExpect(status().isForbidden()));
    }

    @Test
    @DisplayName("Should let the request through when the roles claim has the required role")
    void shouldLetTheRequestThroughWhenTheRolesClaimHasTheRequiredRole() {
        withMockMvc(mockMvc -> mockMvc.perform(bearer(get("/guarded"), issuer.token(REPORTER_ROLE)))
                .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("Should accept a state-changing request that carries a Bearer token without a CSRF token")
    void shouldAcceptAStateChangingRequestThatCarriesABearerTokenWithoutACsrfToken() {
        withMockMvc(mockMvc -> mockMvc.perform(bearer(post("/guarded"), issuer.token(REPORTER_ROLE)))
                .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("Should still answer 401 to a state-changing request with an invalid Bearer token")
    void shouldStillAnswer401ToAStateChangingRequestWithAnInvalidBearerToken() {
        withMockMvc(mockMvc -> mockMvc.perform(bearer(post("/guarded"), "not-a-jwt")).andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("Should keep CSRF protection on for state-changing requests without a Bearer token")
    void shouldKeepCsrfProtectionOnForStateChangingRequestsWithoutABearerToken() {
        withMockMvc(mockMvc -> mockMvc.perform(post("/guarded")).andExpect(status().isForbidden()));
    }

    @Test
    @DisplayName("Should require authentication on endpoints that declare no role")
    void shouldRequireAuthenticationOnEndpointsThatDeclareNoRole() {
        withMockMvc(mockMvc -> {
            mockMvc.perform(get("/open-to-authenticated")).andExpect(status().isUnauthorized());
            mockMvc.perform(bearer(get("/open-to-authenticated"), issuer.token())).andExpect(status().isOk());
        });
    }

    @Test
    @DisplayName("Should require authentication even for paths no controller maps")
    void shouldRequireAuthenticationEvenForPathsNoControllerMaps() {
        withMockMvc(mockMvc -> {
            mockMvc.perform(get("/nowhere")).andExpect(status().isUnauthorized());
            mockMvc.perform(bearer(get("/nowhere"), issuer.token())).andExpect(status().isNotFound());
        });
    }

    @Test
    @DisplayName("Should leave the error path open")
    void shouldLeaveTheErrorPathOpen() {
        withMockMvc(mockMvc -> mockMvc.perform(get("/error")).andExpect(status().isOk()));
    }

    @Test
    @DisplayName("Should leave the health endpoints open")
    void shouldLeaveTheHealthEndpointsOpen() {
        withMockMvc(actuatorRunner, mockMvc -> {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        });
    }

    @Test
    @DisplayName("Should require the metrics role on the prometheus endpoint")
    void shouldRequireTheMetricsRoleOnThePrometheusEndpoint() {
        withMockMvc(actuatorRunner, mockMvc -> assertPrometheusRequiresTheMetricsRole(mockMvc, "/actuator/prometheus"));
    }

    @Test
    @DisplayName("Should keep the Actuator rules when the base path of the endpoints changes")
    void shouldKeepTheActuatorRulesWhenTheBasePathOfTheEndpointsChanges() {
        withMockMvc(actuatorRunner.withPropertyValues("management.endpoints.web.base-path=/manage"), mockMvc -> {
            assertPrometheusRequiresTheMetricsRole(mockMvc, "/manage/prometheus");
            mockMvc.perform(get("/manage/health")).andExpect(status().isOk());
            mockMvc.perform(get("/manage/health/liveness")).andExpect(status().isOk());
        });
    }

    @Test
    @DisplayName("Should keep the metrics rule when the prometheus endpoint is mapped to another path")
    void shouldKeepTheMetricsRuleWhenThePrometheusEndpointIsMappedToAnotherPath() {
        withMockMvc(actuatorRunner.withPropertyValues("management.endpoints.web.path-mapping.prometheus=metrics"),
                mockMvc -> assertPrometheusRequiresTheMetricsRole(mockMvc, "/actuator/metrics"));
    }

    @Test
    @DisplayName("Should require authentication on the Actuator paths when the application has no Actuator")
    void shouldRequireAuthenticationOnTheActuatorPathsWhenTheApplicationHasNoActuator() {
        withMockMvc(mockMvc -> {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
            mockMvc.perform(bearer(get("/actuator/prometheus"), issuer.token(REPORTER_ROLE)))
                    .andExpect(status().isOk());
        });
    }

    private void assertPrometheusRequiresTheMetricsRole(MockMvc mockMvc, String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(bearer(get(path), issuer.token(REPORTER_ROLE))).andExpect(status().isForbidden());
        mockMvc.perform(bearer(get(path), issuer.token(CommonRoles.METRICS_READ))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should not create an HTTP session")
    void shouldNotCreateAnHttpSession() {
        withMockMvc(mockMvc -> {
            MvcResult result = mockMvc.perform(bearer(get("/guarded"), issuer.token(REPORTER_ROLE))).andReturn();

            assertThat(result.getRequest().getSession(false)).isNull();
        });
    }

    @Test
    @DisplayName("Should be discovered by Spring Boot without any import in the applications")
    void shouldBeDiscoveredBySpringBootWithoutAnyImportInTheApplications() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(ResourceServerSecurityAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("Should back off when the application defines its own security filter chain")
    void shouldBackOffWhenTheApplicationDefinesItsOwnSecurityFilterChain() {
        runner.withUserConfiguration(CustomChainConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(SecurityFilterChain.class))
                        .containsOnlyKeys("customSecurityFilterChain"));
    }

    @FunctionalInterface
    private interface MockMvcAssertions {

        void accept(MockMvc mockMvc) throws Exception;

    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebConfiguration {

        @Bean
        GuardedController guardedController() {
            return new GuardedController();
        }

    }

    /** Stand-ins for the health and prometheus endpoints: they register the ids the security rules refer to. */
    @Configuration(proxyBeanMethods = false)
    static class OperationalEndpoints {

        @Bean
        HealthEndpoint healthEndpoint() {
            return new HealthEndpoint();
        }

        @Bean
        PrometheusEndpoint prometheusEndpoint() {
            return new PrometheusEndpoint();
        }

    }

    @WebEndpoint(id = "health")
    static class HealthEndpoint {

        @ReadOperation
        String health() {
            return "ok";
        }

        @ReadOperation
        String component(@Selector String name) {
            return name;
        }

    }

    @WebEndpoint(id = "prometheus")
    static class PrometheusEndpoint {

        @ReadOperation
        String scrape() {
            return "ok";
        }

    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class CustomChainConfiguration {

        @Bean
        SecurityFilterChain customSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll()).build();
        }

    }

    @RestController
    static class GuardedController {

        @GetMapping("/guarded")
        @PreAuthorize("hasRole('" + REPORTER_ROLE + "')")
        String read() {
            return "read";
        }

        @PostMapping("/guarded")
        @PreAuthorize("hasRole('" + REPORTER_ROLE + "')")
        String write() {
            return "write";
        }

        @GetMapping("/open-to-authenticated")
        String openToAuthenticated() {
            return "authenticated";
        }

        /** Serves the operational paths the way the Actuator would, wherever the application moves them. */
        @GetMapping({ BASE_PATH + "/health", BASE_PATH + "/health/liveness", "/error", BASE_PATH + "/prometheus",
                BASE_PATH + "/metrics" })
        String operational() {
            return "ok";
        }

    }

}
