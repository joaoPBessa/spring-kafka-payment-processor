package com.joaopbessa.payments.common.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaopbessa.payments.common.domain.PaymentMethod;

@DisplayName("Package naming (common-libraries)")
class PackageNamingTest {

    @Test
    @DisplayName("Should keep every main and generated package in lower case")
    void shouldKeepEveryMainAndGeneratedPackageInLowerCase() {
        assertThat(PackageNaming.violationsIn(PaymentMethod.class)).isEmpty();
    }

    @Test
    @DisplayName("Should keep every test package in lower case")
    void shouldKeepEveryTestPackageInLowerCase() {
        assertThat(PackageNaming.violationsIn(PackageNamingTest.class)).isEmpty();
    }

}
