package com.joaopbessa.payments.common.security;

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registers the password encoder of the platform: a {@link DelegatingPasswordEncoder} whose stored values carry the id
 * of the encoder that produced them, for example {@code {bcrypt}$2a$10$...}. Passwords are hashed with bcrypt at its
 * default cost, with a salt per password.
 *
 * <p>Another algorithm, or a higher cost, can be added under a new id later, and the values stored with the old id keep
 * working. A value with an id this encoder does not know, or with no id, never matches.
 *
 * <p>bcrypt only reads the first 72 bytes of a password; longer ones are not rejected, the rest is ignored.
 */
@AutoConfiguration
@ConditionalOnClass(PasswordEncoder.class)
public class PasswordEncoderAutoConfiguration {

    static final String BCRYPT_ID = "bcrypt";

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(BCRYPT_ID,
                Map.of(BCRYPT_ID, new BCryptPasswordEncoder()));
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
