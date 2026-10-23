package com.joaoPBessa.payments.consumer.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.joaoPBessa.payments.consumer.domain.entities.Payment;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.domain.PaymentMethod;

@DisplayName("PaymentEventMapper")
class PaymentEventMapperTest {

    private static final String TRANSACTION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SOURCE_ACCOUNT = "123456";
    private static final String TARGET_ACCOUNT = "654321";
    private static final BigDecimal AMOUNT = new BigDecimal("150.75");
    private static final String CURRENCY = "USD";
    private static final Instant EVENT_TIMESTAMP = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private final PaymentEventMapper mapper = new PaymentEventMapper();

    private PaymentEvent eventWith(com.joaopBessa.payments.common.avro.PaymentMethod avroMethod) {
        return PaymentEvent.newBuilder()
                .setTransactionId(TRANSACTION_ID)
                .setEventTimestamp(EVENT_TIMESTAMP)
                .setSourceAccount(SOURCE_ACCOUNT)
                .setTargetAccount(TARGET_ACCOUNT)
                .setAmount(AMOUNT)
                .setCurrency(CURRENCY)
                .setPaymentMethod(avroMethod)
                .build();
    }

    @Test
    @DisplayName("Should copy every event field into a new payment")
    void shouldCopyEveryEventFieldIntoPayment() {
        Payment payment = mapper.toEntity(eventWith(com.joaopBessa.payments.common.avro.PaymentMethod.PIX));

        assertThat(payment.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(payment.getSourceAccount()).isEqualTo(SOURCE_ACCOUNT);
        assertThat(payment.getTargetAccount()).isEqualTo(TARGET_ACCOUNT);
        assertThat(payment.getAmount()).isEqualTo(AMOUNT);
        assertThat(payment.getCurrency()).isEqualTo(CURRENCY);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(payment.getEventTimestamp()).isEqualTo(EVENT_TIMESTAMP);
    }

    @Test
    @DisplayName("Should assign a new id and leave created_at to the database")
    void shouldAssignNewIdAndLeaveCreatedAtToDatabase() {
        Payment payment = mapper.toEntity(eventWith(com.joaopBessa.payments.common.avro.PaymentMethod.PIX));

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getCreatedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(com.joaopBessa.payments.common.avro.PaymentMethod.class)
    @DisplayName("Should map every Avro payment method to the matching domain method")
    void shouldMapEveryAvroPaymentMethod(com.joaopBessa.payments.common.avro.PaymentMethod avroMethod) {
        Payment payment = mapper.toEntity(eventWith(avroMethod));

        assertThat(payment.getPaymentMethod().name()).isEqualTo(avroMethod.name());
    }

}
