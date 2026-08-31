package com.joaoPBessa.payments.producer.controllers;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaoPBessa.payments.producer.exceptions.AccountNotFoundException;
import com.joaoPBessa.payments.producer.exceptions.PaymentPublishException;
import com.joaoPBessa.payments.producer.services.PaymentService;
import com.joaopBessa.payments.common.domain.PaymentMethod;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = PaymentController.class, properties = {
    "spring.cloud.vault.enabled=false",
    "spring.cloud.bootstrap.enabled=false"
})
@DisplayName("PaymentController")
class PaymentControllerTest {

    private static final String TRANSACTION_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    @DisplayName("POST /api/v1/payments -> Success: Should accept a valid payment request and return 202 Accepted")
    void shouldAcceptValidPaymentRequest() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        when(paymentService.publishPayment(request)).thenReturn(TRANSACTION_ID);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.source_account").value("123456"))
                .andExpect(jsonPath("$.target_account").value("654321"))
                .andExpect(jsonPath("$.amount").value(150.75))
                .andExpect(jsonPath("$.transaction_code").value(TRANSACTION_ID));

        verify(paymentService, times(1)).publishPayment(request);
        verifyNoMoreInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Failure: Should return 503 Service Unavailable when Kafka publish fails")
    void shouldReturn503WhenPublishFails() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        when(paymentService.publishPayment(request))
                .thenThrow(new PaymentPublishException("Failed to publish payment event to Kafka within PT5S", new RuntimeException()));

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());

        verify(paymentService, times(1)).publishPayment(request);
        verifyNoMoreInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Failure: Should return 404 Not Found when either account does not exist")
    void shouldReturn404WhenAccountDoesNotExist() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        when(paymentService.publishPayment(request))
                .thenThrow(new AccountNotFoundException("Account 123456 not found"));

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(paymentService, times(1)).publishPayment(request);
        verifyNoMoreInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when source account is blank")
    void shouldReturn400WhenSourceAccountIsBlank() throws Exception {
        var request = new PaymentRequestDTO("   ", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when target account is blank")
    void shouldReturn400WhenTargetAccountIsBlank() throws Exception {
        var request = new PaymentRequestDTO("123456", "   ", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when amount is null")
    void shouldReturn400WhenAmountIsNull() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", null, "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when amount is zero")
    void shouldReturn400WhenAmountIsZero() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", BigDecimal.ZERO, "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when amount is negative")
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("-10.00"), "usd", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency is blank")
    void shouldReturn400WhenCurrencyIsBlank() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "   ", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency is under 3 characters")
    void shouldReturn400WhenCurrencyIsTooShort() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "us", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency exceeds 3 characters")
    void shouldReturn400WhenCurrencyIsTooLong() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usdt", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when payment method is null")
    void shouldReturn400WhenPaymentMethodIsNull() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", null);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when payment method is unrecognized")
    void shouldReturn400WhenPaymentMethodIsUnrecognized() throws Exception {
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

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/v1/payments -> Validation: Should return 400 Bad Request when currency is not alphabetic")
    void shouldReturn400WhenCurrencyIsNotAlphabetic() throws Exception {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "x1!", PaymentMethod.PIX);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

}
