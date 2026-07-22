package com.joaoPBessa.payments.producer.controllers;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaopBessa.payments.common.domain.PaymentMethod;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = PaymentController.class, properties = {
    "spring.cloud.vault.enabled=false",
    "spring.cloud.bootstrap.enabled=false"
})
@DisplayName("PaymentController")
class PaymentControllerTest {

    private static final String UUID_REGEX = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/payments -> Success: Should accept a valid payment request and return 202 Accepted")
    void shouldAcceptValidPaymentRequest() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.source_account").value("123456"))
                .andExpect(jsonPath("$.target_account").value("654321"))
                .andExpect(jsonPath("$.amount").value(150.75))
                .andExpect(jsonPath("$.transaction_code", matchesPattern(UUID_REGEX)));
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when source account is blank")
    void shouldReturn400WhenSourceAccountIsBlank() throws Exception {
        var request = new PaymentRequestDTO("   ", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when target account is blank")
    void shouldReturn400WhenTargetAccountIsBlank() throws Exception {
        var request = new PaymentRequestDTO("123456", "   ", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when amount is null")
    void shouldReturn400WhenAmountIsNull() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", null, "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when amount is zero")
    void shouldReturn400WhenAmountIsZero() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", BigDecimal.ZERO, "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when amount is negative")
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("-10.00"), "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency is blank")
    void shouldReturn400WhenCurrencyIsBlank() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "   ", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency is under 3 characters")
    void shouldReturn400WhenCurrencyIsTooShort() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "us", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency exceeds 3 characters")
    void shouldReturn400WhenCurrencyIsTooLong() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usdt", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when payment method is null")
    void shouldReturn400WhenPaymentMethodIsNull() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", null);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when payment method is unrecognized")
    void shouldReturn400WhenPaymentMethodIsUnrecognized() throws Exception {
        // PaymentMethod can't be constructed with an invalid value in Java, so this uses a raw
        // body: Jackson fails to decode "wire_transfer" while deserializing the request, before
        // Bean Validation ever runs.
        String rawBody = """
                {
                  "source_account": "123456",
                  "target_account": "654321",
                  "amount": 150.75,
                  "currency": "usd",
                  "payment_method": "wire_transfer"
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency is not alphabetic")
    void shouldReturn400WhenCurrencyIsNotAlphabetic() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "x1!", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

}
