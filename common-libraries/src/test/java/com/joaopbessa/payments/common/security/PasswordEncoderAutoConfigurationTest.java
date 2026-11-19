package com.joaopbessa.payments.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("PasswordEncoderAutoConfiguration")
class PasswordEncoderAutoConfigurationTest {

    private static final String RAW_PASSWORD = "demo-password";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PasswordEncoderAutoConfiguration.class));

    @Test
    @DisplayName("Should store new passwords as a bcrypt hash with the bcrypt id prefix, never as the password")
    void shouldStoreNewPasswordsAsABcryptHashWithTheBcryptIdPrefix() {
        runner.run(context -> {
            String stored = context.getBean(PasswordEncoder.class).encode(RAW_PASSWORD);

            assertThat(stored).startsWith("{bcrypt}$2").doesNotContain(RAW_PASSWORD);
        });
    }

    @Test
    @DisplayName("Should salt every hash, so the same password is stored differently each time")
    void shouldSaltEveryHash() {
        runner.run(context -> {
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);

            assertThat(encoder.encode(RAW_PASSWORD)).isNotEqualTo(encoder.encode(RAW_PASSWORD));
        });
    }

    @Test
    @DisplayName("Should match the raw password against a stored bcrypt value, and only that password")
    void shouldMatchTheRawPasswordAgainstAStoredBcryptValue() {
        runner.run(context -> {
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);

            assertThat(encoder.matches(RAW_PASSWORD, encoder.encode(RAW_PASSWORD))).isTrue();
            assertThat(encoder.matches("another-password", encoder.encode(RAW_PASSWORD))).isFalse();
        });
    }

    @Test
    @DisplayName("Should match a bcrypt hash that was made outside the application")
    void shouldMatchABcryptHashThatWasMadeOutsideTheApplication() {
        String hashFromElsewhere = "{bcrypt}" + new BCryptPasswordEncoder().encode(RAW_PASSWORD);

        runner.run(context -> assertThat(context.getBean(PasswordEncoder.class).matches(RAW_PASSWORD, hashFromElsewhere))
                .isTrue());
    }

    @Test
    @DisplayName("Should never match a value stored with the removed base64 id or with an unknown id")
    void shouldNeverMatchAValueStoredWithTheRemovedBase64IdOrWithAnUnknownId() {
        runner.run(context -> {
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);

            assertThat(encoder.matches(RAW_PASSWORD, "{base64}ZGVtby1wYXNzd29yZA==")).isFalse();
            assertThat(encoder.matches(RAW_PASSWORD, "{unknown}whatever")).isFalse();
        });
    }

    @Test
    @DisplayName("Should not match a stored value that has no id prefix")
    void shouldNotMatchAStoredValueThatHasNoIdPrefix() {
        String hashWithoutPrefix = new BCryptPasswordEncoder().encode(RAW_PASSWORD);

        runner.run(context -> assertThat(context.getBean(PasswordEncoder.class).matches(RAW_PASSWORD, hashWithoutPrefix))
                .isFalse());
    }

    @Test
    @DisplayName("Should be discovered by Spring Boot without any import in the applications")
    void shouldBeDiscoveredBySpringBootWithoutAnyImportInTheApplications() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(PasswordEncoderAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("Should back off when the application defines its own password encoder")
    void shouldBackOffWhenTheApplicationDefinesItsOwnPasswordEncoder() {
        PasswordEncoder custom = new BCryptPasswordEncoder();

        runner.withBean("customPasswordEncoder", PasswordEncoder.class, () -> custom)
                .run(context -> assertThat(context.getBeansOfType(PasswordEncoder.class))
                        .containsOnlyKeys("customPasswordEncoder"));
    }

    @Test
    @DisplayName("Should stay out of the way when Spring Security Crypto is not on the classpath")
    void shouldStayOutOfTheWayWhenSpringSecurityCryptoIsNotOnTheClasspath() {
        runner.withClassLoader(new FilteredClassLoader(PasswordEncoder.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("passwordEncoder");
        });
    }

}
