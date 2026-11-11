package com.joaopbessa.payments.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.stream.Stream;

public enum PaymentMethod {

	CREDIT_CARD("credit_card", com.joaopbessa.payments.common.avro.PaymentMethod.CREDIT_CARD),
    DEBIT_CARD("debit_card", com.joaopbessa.payments.common.avro.PaymentMethod.DEBIT_CARD),
    PIX("pix", com.joaopbessa.payments.common.avro.PaymentMethod.PIX),
    BANK_TRANSFER("bank_transfer", com.joaopbessa.payments.common.avro.PaymentMethod.BANK_TRANSFER),
    CRYPTO("crypto", com.joaopbessa.payments.common.avro.PaymentMethod.CRYPTO);

    private final String value;
    private final com.joaopbessa.payments.common.avro.PaymentMethod avroValue;

    PaymentMethod(String value, com.joaopbessa.payments.common.avro.PaymentMethod avroValue) {
        this.value = value;
        this.avroValue = avroValue;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public com.joaopbessa.payments.common.avro.PaymentMethod toAvroPaymentMethod() {
        return avroValue;
    }

    public static PaymentMethod fromAvro(com.joaopbessa.payments.common.avro.PaymentMethod avroMethod) {
        return Stream.of(PaymentMethod.values())
                .filter(targetEnum -> targetEnum.avroValue == avroMethod)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Avro payment method: " + avroMethod));
    }

    @JsonCreator
    public static PaymentMethod decode(String code) {
        return Stream.of(PaymentMethod.values())
                .filter(targetEnum -> targetEnum.value.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method: " + code));
    }

}
