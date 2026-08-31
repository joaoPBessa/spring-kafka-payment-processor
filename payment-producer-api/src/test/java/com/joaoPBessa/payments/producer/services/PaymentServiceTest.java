package com.joaoPBessa.payments.producer.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.joaoPBessa.payments.producer.api.dto.request.PaymentRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.AccountResponseDTO;
import com.joaoPBessa.payments.producer.config.KafkaProducerProperties;
import com.joaoPBessa.payments.producer.exceptions.AccountNotFoundException;
import com.joaoPBessa.payments.producer.exceptions.PaymentPublishException;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.domain.PaymentMethod;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    private static final String UUID_REGEX = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final String TOPIC = "payment.events";

    @Mock
    private KafkaTemplate<String, PaymentEvent> paymentEventKafkaTemplate;

    @Mock
    private AccountService accountService;

    @Captor
    private ArgumentCaptor<PaymentEvent> eventCaptor;

    private PaymentService serviceWithTimeout(Duration timeout) {
        var properties = new KafkaProducerProperties(
                new KafkaProducerProperties.Topics(TOPIC),
                new KafkaProducerProperties.Producer(timeout));
        return new PaymentService(paymentEventKafkaTemplate, properties, accountService);
    }

    private void stubBothAccountsExist(String sourceAccount, String targetAccount) {
        when(accountService.findByNumber(sourceAccount)).thenReturn(mock(AccountResponseDTO.class));
        when(accountService.findByNumber(targetAccount)).thenReturn(mock(AccountResponseDTO.class));
    }

    @Test
    @DisplayName("publishPayment -> Success: Should publish the event keyed by source account and return the generated transaction id")
    void shouldPublishPaymentEventSuccessfully() {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);
        stubBothAccountsExist("123456", "654321");
        SendResult<String, PaymentEvent> sendResult = mock();
        var future = CompletableFuture.completedFuture(sendResult);

        when(paymentEventKafkaTemplate.send(eq(TOPIC), eq("123456"), eventCaptor.capture())).thenReturn(future);

        String transactionId = serviceWithTimeout(Duration.ofSeconds(5)).publishPayment(request);

        assertThat(transactionId).matches(UUID_REGEX);

        PaymentEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getTransactionId()).isEqualTo(transactionId);
        assertThat(publishedEvent.getSourceAccount()).isEqualTo("123456");
        assertThat(publishedEvent.getTargetAccount()).isEqualTo("654321");
        assertThat(publishedEvent.getAmount()).isEqualByComparingTo("150.75");
        assertThat(publishedEvent.getCurrency()).isEqualTo("USD");
        assertThat(publishedEvent.getPaymentMethod()).isEqualTo(com.joaopBessa.payments.common.avro.PaymentMethod.PIX);
        assertThat(publishedEvent.getEventTimestamp()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

        verify(accountService, times(1)).findByNumber("123456");
        verify(accountService, times(1)).findByNumber("654321");
        verifyNoMoreInteractions(accountService);
        verify(paymentEventKafkaTemplate, times(1)).send(TOPIC, "123456", publishedEvent);
        verifyNoMoreInteractions(paymentEventKafkaTemplate);
    }

    @Test
    @DisplayName("publishPayment -> Correctness: Should round the amount to 2 decimal places to match the Avro schema's fixed scale")
    void shouldNormalizeAmountScaleBeforePublishing() {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.755"), "usd", PaymentMethod.PIX);
        stubBothAccountsExist("123456", "654321");
        SendResult<String, PaymentEvent> sendResult = mock();
        var future = CompletableFuture.completedFuture(sendResult);

        when(paymentEventKafkaTemplate.send(eq(TOPIC), eq("123456"), eventCaptor.capture())).thenReturn(future);

        serviceWithTimeout(Duration.ofSeconds(5)).publishPayment(request);

        assertThat(eventCaptor.getValue().getAmount()).isEqualByComparingTo("150.76");
        verify(paymentEventKafkaTemplate, times(1)).send(TOPIC, "123456", eventCaptor.getValue());
    }

    @Test
    @DisplayName("publishPayment -> Failure: Should throw PaymentPublishException when the broker doesn't confirm within the timeout")
    void shouldThrowPaymentPublishExceptionOnTimeout() {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);
        stubBothAccountsExist("123456", "654321");
        var neverCompletes = new CompletableFuture<SendResult<String, PaymentEvent>>();

        when(paymentEventKafkaTemplate.send(eq(TOPIC), eq("123456"), eventCaptor.capture())).thenReturn(neverCompletes);

        assertThatThrownBy(() -> serviceWithTimeout(Duration.ofMillis(50)).publishPayment(request))
                .isInstanceOf(PaymentPublishException.class)
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);

        verify(paymentEventKafkaTemplate, times(1)).send(TOPIC, "123456", eventCaptor.getValue());
    }

    @Test
    @DisplayName("publishPayment -> Validation: Should throw AccountNotFoundException and never publish when the source account does not exist")
    void shouldThrowAccountNotFoundExceptionAndNeverPublishWhenSourceAccountDoesNotExist() {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        when(accountService.findByNumber("123456")).thenThrow(new AccountNotFoundException("Account 123456 not found"));

        assertThatThrownBy(() -> serviceWithTimeout(Duration.ofSeconds(5)).publishPayment(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account 123456 not found");

        verify(accountService, times(1)).findByNumber("123456");
        verifyNoMoreInteractions(accountService);
        verifyNoInteractions(paymentEventKafkaTemplate);
    }

    @Test
    @DisplayName("publishPayment -> Validation: Should throw AccountNotFoundException and never publish when the target account does not exist")
    void shouldThrowAccountNotFoundExceptionAndNeverPublishWhenTargetAccountDoesNotExist() {
        var request = new PaymentRequestDTO("123456", "654321", new BigDecimal("150.75"), "usd", PaymentMethod.PIX);

        when(accountService.findByNumber("123456")).thenReturn(mock(AccountResponseDTO.class));
        when(accountService.findByNumber("654321")).thenThrow(new AccountNotFoundException("Account 654321 not found"));

        assertThatThrownBy(() -> serviceWithTimeout(Duration.ofSeconds(5)).publishPayment(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account 654321 not found");

        verify(accountService, times(1)).findByNumber("123456");
        verify(accountService, times(1)).findByNumber("654321");
        verifyNoMoreInteractions(accountService);
        verifyNoInteractions(paymentEventKafkaTemplate);
    }

}
