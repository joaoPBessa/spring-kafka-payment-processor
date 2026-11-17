package com.joaopbessa.payments.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaopbessa.payments.common.naming.PackageNaming;

@DisplayName("Package naming (payment-auth-server)")
class PackageNamingTest {

    @Test
    @DisplayName("Should keep every main package in lower case")
    void shouldKeepEveryMainPackageInLowerCase() {
        assertThat(PackageNaming.violationsIn(PaymentAuthServerApplication.class)).isEmpty();
    }

    @Test
    @DisplayName("Should keep every test package in lower case")
    void shouldKeepEveryTestPackageInLowerCase() {
        assertThat(PackageNaming.violationsIn(PackageNamingTest.class)).isEmpty();
    }

}
