package com.joaopBessa.payments.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("PaymentMethod")
class PaymentMethodTest {

    @ParameterizedTest
    @EnumSource(com.joaopBessa.payments.common.avro.PaymentMethod.class)
    @DisplayName("Should map every Avro symbol to the domain constant with the same name")
    void shouldMapEveryAvroSymbolToDomainConstant(com.joaopBessa.payments.common.avro.PaymentMethod avroMethod) {
        PaymentMethod domainMethod = PaymentMethod.fromAvro(avroMethod);

        assertThat(domainMethod.name()).isEqualTo(avroMethod.name());
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    @DisplayName("Should round-trip every domain constant through its Avro counterpart")
    void shouldRoundTripEveryDomainConstantThroughAvro(PaymentMethod domainMethod) {
        PaymentMethod roundTripped = PaymentMethod.fromAvro(domainMethod.toAvroPaymentMethod());

        assertThat(roundTripped).isEqualTo(domainMethod);
    }

    @Test
    @DisplayName("Should reject a null Avro payment method")
    void shouldRejectNullAvroPaymentMethod() {
        assertThatThrownBy(() -> PaymentMethod.fromAvro(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown Avro payment method: null");
    }

}
