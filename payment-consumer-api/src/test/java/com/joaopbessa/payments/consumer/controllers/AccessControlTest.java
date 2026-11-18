package com.joaopbessa.payments.consumer.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.joaopbessa.payments.common.security.CommonRoles;
import com.joaopbessa.payments.common.security.ResourceServerSecurityAutoConfiguration;
import com.joaopbessa.payments.common.security.TestJwtIssuer;
import com.joaopbessa.payments.consumer.TestJwtConfiguration;
import com.joaopbessa.payments.consumer.dlt.DeadLetterReplayService;
import com.joaopbessa.payments.consumer.dlt.DeadLetterService;
import com.joaopbessa.payments.consumer.dlt.ReplayResult;
import com.joaopbessa.payments.consumer.security.Roles;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = DeadLetterController.class, properties = {
        "spring.cloud.vault.enabled=false", "spring.cloud.bootstrap.enabled=false" })
@Import({ ResourceServerSecurityAutoConfiguration.class, TestJwtConfiguration.class })
@DisplayName("Access control of the dead letter endpoints")
class AccessControlTest {

    private static final String URL = "/api/v1/dead-letters";
    private static final String REPLAY_URL = "/api/v1/dead-letters/replay";
    private static final String PRODUCER_ROLE = "ACCOUNT_WRITE";
    private static final List<String> ALL_ROLES = List.of(
            Roles.DLT_VIEW, Roles.DLT_REPLAY, CommonRoles.METRICS_READ, PRODUCER_ROLE);

    private record Endpoint(HttpMethod method, String path, Object body, String requiredRole) {

        boolean changesState() {
            return !HttpMethod.GET.equals(method);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }

    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.GET, URL, null, Roles.DLT_VIEW),
                new Endpoint(HttpMethod.POST, REPLAY_URL, Map.of("transaction_ids", List.of("tx-1")), Roles.DLT_REPLAY))
                .map(Arguments::of);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TestJwtIssuer issuer;

    @MockitoBean
    private DeadLetterService deadLetterService;

    @MockitoBean
    private DeadLetterReplayService deadLetterReplayService;

    private MockHttpServletRequestBuilder requestTo(Endpoint endpoint) throws Exception {
        MockHttpServletRequestBuilder request = request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(endpoint.body()));
        }
        return request;
    }

    private int statusOf(Endpoint endpoint, String authorization) throws Exception {
        MockHttpServletRequestBuilder request = requestTo(endpoint);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private void givenTheServicesAnswer() {
        when(deadLetterReplayService.replay(List.of("tx-1")))
                .thenReturn(List.of(ReplayResult.replayed("tx-1", 0, 3)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Should reject a request without a token before it reaches the service")
    void shouldRejectARequestWithoutAToken(Endpoint endpoint) throws Exception {
        // A request that changes state without credentials fails the CSRF check (403), which runs before the
        // authentication; a read has nothing to check but the token (401).
        int expected = endpoint.changesState() ? 403 : 401;

        assertThat(statusOf(endpoint, null)).isEqualTo(expected);
        verifyNoInteractions(deadLetterService, deadLetterReplayService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Should answer 401 to a token that is invalid, expired or signed by someone else")
    void shouldAnswer401ToATokenThatCannotBeTrusted(Endpoint endpoint) throws Exception {
        String garbage = "Bearer not-a-jwt";
        String expired = "Bearer " + issuer.expiredToken(endpoint.requiredRole());
        String foreign = new TestJwtIssuer().bearer(endpoint.requiredRole());

        assertThat(statusOf(endpoint, garbage)).isEqualTo(401);
        assertThat(statusOf(endpoint, expired)).isEqualTo(401);
        assertThat(statusOf(endpoint, foreign)).isEqualTo(401);
        verifyNoInteractions(deadLetterService, deadLetterReplayService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Should answer 403 to a valid token that has any role but the required one, or none")
    void shouldAnswer403WithoutTheRequiredRole(Endpoint endpoint) throws Exception {
        List<String> otherRoles = ALL_ROLES.stream().filter(role -> !role.equals(endpoint.requiredRole())).toList();

        for (String role : otherRoles) {
            assertThat(statusOf(endpoint, issuer.bearer(role))).as("role %s", role).isEqualTo(403);
        }
        assertThat(statusOf(endpoint, issuer.bearer())).as("no roles").isEqualTo(403);
        verifyNoInteractions(deadLetterService, deadLetterReplayService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Should let a token with the required role through")
    void shouldLetTheRequiredRoleThrough(Endpoint endpoint) throws Exception {
        givenTheServicesAnswer();

        assertThat(statusOf(endpoint, issuer.bearer(endpoint.requiredRole()))).isBetween(200, 299);
    }

    @Test
    @DisplayName("Should not let a viewer replay, nor a replayer view: the roles do not imply each other")
    void shouldNotLetTheRolesImplyEachOther() throws Exception {
        Endpoint view = new Endpoint(HttpMethod.GET, URL, null, Roles.DLT_VIEW);
        Endpoint replay = new Endpoint(HttpMethod.POST, REPLAY_URL, Map.of("transaction_ids", List.of("tx-1")),
                Roles.DLT_REPLAY);

        assertThat(statusOf(replay, issuer.bearer(Roles.DLT_VIEW))).isEqualTo(403);
        assertThat(statusOf(view, issuer.bearer(Roles.DLT_REPLAY))).isEqualTo(403);
    }

    @Test
    @DisplayName("Should answer 403 as a problem detail, like the other errors of the API")
    void shouldAnswer403AsAProblemDetail() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, URL).header(HttpHeaders.AUTHORIZATION, issuer.bearer(Roles.DLT_REPLAY)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

}
