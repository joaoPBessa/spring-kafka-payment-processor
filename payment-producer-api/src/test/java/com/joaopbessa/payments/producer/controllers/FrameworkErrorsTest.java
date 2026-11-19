package com.joaopbessa.payments.producer.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.joaopbessa.payments.common.security.ResourceServerSecurityAutoConfiguration;
import com.joaopbessa.payments.common.security.TestJwtIssuer;
import com.joaopbessa.payments.producer.TestJwtConfiguration;
import com.joaopbessa.payments.producer.security.Roles;
import com.joaopbessa.payments.producer.services.AccountService;
import com.joaopbessa.payments.producer.services.PaymentService;

@WebMvcTest(controllers = { AccountController.class, PaymentController.class }, properties = {
    "spring.cloud.vault.enabled=false",
    "spring.cloud.bootstrap.enabled=false"
})
@Import({ ResourceServerSecurityAutoConfiguration.class, TestJwtConfiguration.class })
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Errors raised by Spring MVC itself, and the ones nobody handles")
class FrameworkErrorsTest {

    private static final String ACCOUNT_NUMBER = "123456";
    private static final String UNEXPECTED_ERROR = "An unexpected error occurred";
    private static final String UNHANDLED_EXCEPTION_LOG = "Unhandled exception while processing request";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestJwtIssuer issuer;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    @DisplayName("Should answer 404, not 500, to a path that does not exist, and not log it as an error")
    void shouldAnswer404ToAPathThatDoesNotExist(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/nothing").header(HttpHeaders.AUTHORIZATION, issuer.bearer(Roles.ACCOUNT_READ)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(output.getAll()).doesNotContain(UNHANDLED_EXCEPTION_LOG);
        verifyNoInteractions(accountService, paymentService);
    }

    @Test
    @DisplayName("Should answer 405 with the Allow header to a method the path does not support")
    void shouldAnswer405ToAMethodThePathDoesNotSupport() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/" + ACCOUNT_NUMBER)
                .header(HttpHeaders.AUTHORIZATION, issuer.bearer(Roles.ACCOUNT_WRITE)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));

        verifyNoInteractions(accountService, paymentService);
    }

    @Test
    @DisplayName("Should answer 415 to a body in a media type the endpoint does not read")
    void shouldAnswer415ToABodyInAMediaTypeTheEndpointDoesNotRead() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, issuer.bearer(Roles.PAYMENT_CREATE))
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        verifyNoInteractions(accountService, paymentService);
    }

    @Test
    @DisplayName("Should keep answering 500 and logging the error when the application itself fails")
    void shouldKeepAnswering500WhenTheApplicationItselfFails(CapturedOutput output) throws Exception {
        when(accountService.findByNumber(ACCOUNT_NUMBER)).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/v1/accounts/" + ACCOUNT_NUMBER)
                .header(HttpHeaders.AUTHORIZATION, issuer.bearer(Roles.ACCOUNT_READ)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(UNEXPECTED_ERROR));

        assertThat(output.getAll()).contains(UNHANDLED_EXCEPTION_LOG);
        verify(accountService, times(1)).findByNumber(ACCOUNT_NUMBER);
        verifyNoMoreInteractions(accountService);
        verifyNoInteractions(paymentService);
    }

}
