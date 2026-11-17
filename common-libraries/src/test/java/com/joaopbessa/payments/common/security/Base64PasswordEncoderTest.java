package com.joaopbessa.payments.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Base64PasswordEncoder")
class Base64PasswordEncoderTest {

    private static final String RAW_PASSWORD = "demo-password";
    private static final String RAW_PASSWORD_AS_BASE64 = "ZGVtby1wYXNzd29yZA==";

    private final Base64PasswordEncoder encoder = new Base64PasswordEncoder();

    @Test
    @DisplayName("Should encode the password as the Base64 of its UTF-8 bytes")
    void shouldEncodeThePasswordAsTheBase64OfItsUtf8Bytes() {
        assertThat(encoder.encode(RAW_PASSWORD)).isEqualTo(RAW_PASSWORD_AS_BASE64);
    }

    @Test
    @DisplayName("Should never store the raw password")
    void shouldNeverStoreTheRawPassword() {
        assertThat(encoder.encode(RAW_PASSWORD)).isNotEqualTo(RAW_PASSWORD);
    }

    @Test
    @DisplayName("Should match the raw password against its encoded value")
    void shouldMatchTheRawPasswordAgainstItsEncodedValue() {
        assertThat(encoder.matches(RAW_PASSWORD, RAW_PASSWORD_AS_BASE64)).isTrue();
    }

    @Test
    @DisplayName("Should not match a different password")
    void shouldNotMatchADifferentPassword() {
        assertThat(encoder.matches("another-password", RAW_PASSWORD_AS_BASE64)).isFalse();
    }

    @Test
    @DisplayName("Should not match when the stored value is the raw password")
    void shouldNotMatchWhenTheStoredValueIsTheRawPassword() {
        assertThat(encoder.matches(RAW_PASSWORD, RAW_PASSWORD)).isFalse();
    }

    @Test
    @DisplayName("Should round-trip passwords with non-ASCII characters")
    void shouldRoundTripPasswordsWithNonAsciiCharacters() {
        String password = "sênha-çom-acentos-€";

        assertThat(encoder.matches(password, encoder.encode(password))).isTrue();
    }

    @Test
    @DisplayName("Should not match a null or empty stored value")
    void shouldNotMatchANullOrEmptyStoredValue() {
        assertThat(encoder.matches(RAW_PASSWORD, null)).isFalse();
        assertThat(encoder.matches(RAW_PASSWORD, "")).isFalse();
    }

    @Test
    @DisplayName("Should never ask for the stored value to be upgraded")
    void shouldNeverAskForTheStoredValueToBeUpgraded() {
        assertThat(encoder.upgradeEncoding(RAW_PASSWORD_AS_BASE64)).isFalse();
    }

}
