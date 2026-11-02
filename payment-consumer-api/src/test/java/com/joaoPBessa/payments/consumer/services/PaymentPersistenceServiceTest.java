package com.joaoPBessa.payments.consumer.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import com.joaoPBessa.payments.consumer.domain.entities.Payment;
import com.joaoPBessa.payments.consumer.mappers.PaymentEventMapper;
import com.joaoPBessa.payments.consumer.repositories.PaymentRepository;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.domain.PaymentMethod;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentPersistenceService")
class PaymentPersistenceServiceTest {

    private static final String TRANSACTION_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventMapper paymentEventMapper;

    @InjectMocks
    private PaymentPersistenceService service;

    private final PaymentEvent event = PaymentEvent.newBuilder()
            .setTransactionId(TRANSACTION_ID)
            .setEventTimestamp(Instant.now())
            .setSourceAccount("123456")
            .setTargetAccount("654321")
            .setAmount(new BigDecimal("150.75"))
            .setCurrency("USD")
            .setPaymentMethod(com.joaopBessa.payments.common.avro.PaymentMethod.PIX)
            .build();

    private final Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .transactionId(TRANSACTION_ID)
            .paymentMethod(PaymentMethod.PIX)
            .build();

    @Test
    @DisplayName("Should save the payment when the transaction id is new")
    void shouldSavePaymentWhenTransactionIdIsNew() {
        when(paymentRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(false);
        when(paymentEventMapper.toEntity(event)).thenReturn(payment);

        service.persist(event);

        verify(paymentRepository, times(1)).existsByTransactionId(TRANSACTION_ID);
        verify(paymentEventMapper, times(1)).toEntity(event);
        verify(paymentRepository, times(1)).saveAndFlush(payment);
        verifyNoMoreInteractions(paymentRepository, paymentEventMapper);
    }

    @Test
    @DisplayName("Should skip the event when the transaction id was already persisted")
    void shouldSkipEventWhenTransactionIdWasAlreadyPersisted() {
        when(paymentRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(true);

        service.persist(event);

        verify(paymentRepository, times(1)).existsByTransactionId(TRANSACTION_ID);
        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(paymentEventMapper);
    }

    @Test
    @DisplayName("Should treat a concurrent duplicate as already persisted")
    void shouldTreatConcurrentDuplicateAsAlreadyPersisted() {
        when(paymentRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(false, true);
        when(paymentEventMapper.toEntity(event)).thenReturn(payment);
        when(paymentRepository.saveAndFlush(payment)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> service.persist(event)).doesNotThrowAnyException();

        verify(paymentRepository, times(2)).existsByTransactionId(TRANSACTION_ID);
        verify(paymentEventMapper, times(1)).toEntity(event);
        verify(paymentRepository, times(1)).saveAndFlush(payment);
        verifyNoMoreInteractions(paymentRepository, paymentEventMapper);
    }

    @Test
    @DisplayName("Should rethrow an integrity violation when the payment was not persisted")
    void shouldRethrowIntegrityViolationWhenPaymentWasNotPersisted() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("unknown account");
        when(paymentRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(false, false);
        when(paymentEventMapper.toEntity(event)).thenReturn(payment);
        when(paymentRepository.saveAndFlush(payment)).thenThrow(violation);

        assertThatThrownBy(() -> service.persist(event)).isSameAs(violation);

        verify(paymentRepository, times(2)).existsByTransactionId(TRANSACTION_ID);
        verify(paymentEventMapper, times(1)).toEntity(event);
        verify(paymentRepository, times(1)).saveAndFlush(payment);
        verifyNoMoreInteractions(paymentRepository, paymentEventMapper);
    }

    @Test
    @DisplayName("Should propagate failures that are not integrity violations")
    void shouldPropagateFailuresThatAreNotIntegrityViolations() {
        QueryTimeoutException timeout = new QueryTimeoutException("db timeout");
        when(paymentRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(false);
        when(paymentEventMapper.toEntity(event)).thenReturn(payment);
        when(paymentRepository.saveAndFlush(payment)).thenThrow(timeout);

        assertThatThrownBy(() -> service.persist(event)).isSameAs(timeout);

        verify(paymentRepository, times(1)).existsByTransactionId(TRANSACTION_ID);
        verify(paymentEventMapper, times(1)).toEntity(event);
        verify(paymentRepository, times(1)).saveAndFlush(payment);
        verifyNoMoreInteractions(paymentRepository, paymentEventMapper);
    }

}
