package com.joaopbessa.payments.common.security;

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registers the password encoder of the platform: a {@link DelegatingPasswordEncoder} whose stored values carry the id
 * of the encoder that produced them (for example {@code {base64}ZGVtbw==}). A stronger encoder can be added under a
 * new id later, and values stored with the old id keep working.
 */
@AutoConfiguration
@ConditionalOnClass(PasswordEncoder.class)
public class PasswordEncoderAutoConfiguration {

    static final String BASE64_ID = "base64";

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(BASE64_ID,
                Map.of(BASE64_ID, new Base64PasswordEncoder()));
        encoder.setDefaultPasswordEncoderForMatches(new RejectingPasswordEncoder());
        return encoder;
    }

    /**
     * Used for stored values without a known id prefix: they never match, instead of failing the authentication with
     * an exception.
     */
    private static final class RejectingPasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            throw new UnsupportedOperationException("Passwords are never encoded without an id prefix");
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return false;
        }

    }

}
