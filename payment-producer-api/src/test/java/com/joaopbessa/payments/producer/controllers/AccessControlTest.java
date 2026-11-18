package com.joaopbessa.payments.producer.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.joaopbessa.payments.common.domain.PaymentMethod;
import com.joaopbessa.payments.common.security.CommonRoles;
import com.joaopbessa.payments.common.security.ResourceServerSecurityAutoConfiguration;
import com.joaopbessa.payments.common.security.TestJwtIssuer;
import com.joaopbessa.payments.producer.TestJwtConfiguration;
import com.joaopbessa.payments.producer.api.dto.request.CreateAccountRequestDTO;
import com.joaopbessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaopbessa.payments.producer.api.dto.request.UpdateAccountRequestDTO;
import com.joaopbessa.payments.producer.api.dto.response.AccountResponseDTO;
import com.joaopbessa.payments.producer.domain.entities.Account;
import com.joaopbessa.payments.producer.security.Roles;
import com.joaopbessa.payments.producer.services.AccountService;
import com.joaopbessa.payments.producer.services.PaymentService;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = { AccountController.class, PaymentController.class }, properties = {
    "spring.cloud.vault.enabled=false",
    "spring.cloud.bootstrap.enabled=false"
})
@Import({ ResourceServerSecurityAutoConfiguration.class, TestJwtConfiguration.class })
@DisplayName("Access control of the producer endpoints")
class AccessControlTest {

    private static final String ACCOUNT_NUMBER = "123456";
    private static final List<String> ALL_ROLES = List.of(
            Roles.ACCOUNT_READ, Roles.ACCOUNT_WRITE, Roles.PAYMENT_CREATE, CommonRoles.METRICS_READ);

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
                new Endpoint(HttpMethod.GET, "/api/v1/accounts/" + ACCOUNT_NUMBER, null, Roles.ACCOUNT_READ),
                new Endpoint(HttpMethod.GET, "/api/v1/accounts?page=0&size=10", null, Roles.ACCOUNT_READ),
                new Endpoint(HttpMethod.POST, "/api/v1/accounts/",
                        new CreateAccountRequestDTO(ACCOUNT_NUMBER, "João Pedro"), Roles.ACCOUNT_WRITE),
                new Endpoint(HttpMethod.PATCH, "/api/v1/accounts/" + ACCOUNT_NUMBER,
                        new UpdateAccountRequestDTO("New name"), Roles.ACCOUNT_WRITE),
                new Endpoint(HttpMethod.DELETE, "/api/v1/accounts/" + ACCOUNT_NUMBER, null, Roles.ACCOUNT_WRITE),
                new Endpoint(HttpMethod.POST, "/api/v1/payments",
                        new PaymentRequestDTO(ACCOUNT_NUMBER, "654321", new BigDecimal("10.00"), "usd", PaymentMethod.PIX),
                        Roles.PAYMENT_CREATE))
                .map(Arguments::of);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TestJwtIssuer issuer;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private PaymentService paymentService;

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
        // The account id is a random UUID assigned while handling the request, so only the number can be matched.
        ArgumentMatcher<Account> requestedAccount = account -> ACCOUNT_NUMBER.equals(account.getNumber());
        when(accountService.save(argThat(requestedAccount)))
                .thenReturn(new AccountResponseDTO(ACCOUNT_NUMBER, "João Pedro", true, LocalDateTime.now()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Should reject a request without a token before it reaches the service")
    void shouldRejectARequestWithoutAToken(Endpoint endpoint) throws Exception {
        // A request that changes state without credentials fails the CSRF check (403), which runs before the
        // authentication; a read has nothing to check but the token (401).
        int expected = endpoint.changesState() ? 403 : 401;

        assertThat(statusOf(endpoint, null)).isEqualTo(expected);
        verifyNoInteractions(accountService, paymentService);
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
        verifyNoInteractions(accountService, paymentService);
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
        verifyNoInteractions(accountService, paymentService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Should let a token with the required role through")
    void shouldLetTheRequiredRoleThrough(Endpoint endpoint) throws Exception {
        givenTheServicesAnswer();

        assertThat(statusOf(endpoint, issuer.bearer(endpoint.requiredRole()))).isBetween(200, 299);
    }

    @Test
    @DisplayName("Should answer 403 in the error format of the API, not as an unexpected 500")
    void shouldAnswer403InTheErrorFormatOfTheApi() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/accounts/" + ACCOUNT_NUMBER)
                .header(HttpHeaders.AUTHORIZATION, issuer.bearer(Roles.PAYMENT_CREATE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

}
