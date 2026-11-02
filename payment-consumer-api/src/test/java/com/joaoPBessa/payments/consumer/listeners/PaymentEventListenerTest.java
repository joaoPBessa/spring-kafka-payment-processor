package com.joaoPBessa.payments.consumer.listeners;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import com.joaoPBessa.payments.consumer.services.PaymentPersistenceService;
import com.joaopBessa.payments.common.avro.PaymentEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventListener")
class PaymentEventListenerTest {

    @Mock
    private PaymentPersistenceService paymentPersistenceService;

    @InjectMocks
    private PaymentEventListener listener;

    private final PaymentEvent event = PaymentEvent.newBuilder()
            .setTransactionId("11111111-1111-1111-1111-111111111111")
            .setEventTimestamp(Instant.now())
            .setSourceAccount("123456")
            .setTargetAccount("654321")
            .setAmount(new BigDecimal("150.75"))
            .setCurrency("USD")
            .setPaymentMethod(com.joaopBessa.payments.common.avro.PaymentMethod.PIX)
            .build();

    @Test
    @DisplayName("Should delegate the received event to the persistence service")
    void shouldDelegateReceivedEventToPersistenceService() {
        listener.onPaymentEvent(event);

        verify(paymentPersistenceService, times(1)).persist(event);
        verifyNoMoreInteractions(paymentPersistenceService);
    }

    @Test
    @DisplayName("Should let persistence failures reach the container error handler unchanged")
    void shouldLetPersistenceFailuresReachErrorHandlerUnchanged() {
        QueryTimeoutException failure = new QueryTimeoutException("db timeout");
        doThrow(failure).when(paymentPersistenceService).persist(event);

        assertThatThrownBy(() -> listener.onPaymentEvent(event)).isSameAs(failure);

        verify(paymentPersistenceService, times(1)).persist(event);
        verifyNoMoreInteractions(paymentPersistenceService);
    }

}
