package com.joaopBessa.payments.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.stream.Stream;

public enum PaymentMethod {
	
	CREDIT_CARD("credit_card"),
    DEBIT_CARD("debit_card"),
    PIX("pix"),
    BANK_TRANSFER("bank_transfer"),
    CRYPTO("crypto");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator // Permite que o Jackson converta a String do JSON de volta para o Enum
    public static PaymentMethod decode(String code) {
        return Stream.of(PaymentMethod.values())
                .filter(targetEnum -> targetEnum.value.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method: " + code));
    }

}
