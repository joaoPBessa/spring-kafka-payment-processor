package com.joaopBessa.payments.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.stream.Stream;

public enum PaymentMethod {

	CREDIT_CARD("credit_card", com.joaopBessa.payments.common.avro.PaymentMethod.CREDIT_CARD),
    DEBIT_CARD("debit_card", com.joaopBessa.payments.common.avro.PaymentMethod.DEBIT_CARD),
    PIX("pix", com.joaopBessa.payments.common.avro.PaymentMethod.PIX),
    BANK_TRANSFER("bank_transfer", com.joaopBessa.payments.common.avro.PaymentMethod.BANK_TRANSFER),
    CRYPTO("crypto", com.joaopBessa.payments.common.avro.PaymentMethod.CRYPTO);

    private final String value;
    private final com.joaopBessa.payments.common.avro.PaymentMethod avroValue;

    PaymentMethod(String value, com.joaopBessa.payments.common.avro.PaymentMethod avroValue) {
        this.value = value;
        this.avroValue = avroValue;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public com.joaopBessa.payments.common.avro.PaymentMethod toAvroPaymentMethod() {
        return avroValue;
    }

    @JsonCreator
    public static PaymentMethod decode(String code) {
        return Stream.of(PaymentMethod.values())
                .filter(targetEnum -> targetEnum.value.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method: " + code));
    }

}
