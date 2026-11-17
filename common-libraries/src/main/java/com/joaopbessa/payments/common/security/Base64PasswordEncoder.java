package com.joaopbessa.payments.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Encodes passwords as Base64. <strong>This is an encoding, not a hash: anyone who can read the stored value can
 * recover the password.</strong> It exists as a demonstration stand-in behind the {@link PasswordEncoder} contract, so
 * it can be replaced by a real hash (for example bcrypt) without touching the code that authenticates users. Do not
 * use it for real credentials.
 */
public class Base64PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        return Base64.getEncoder().encodeToString(bytesOf(rawPassword.toString()));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(bytesOf(encode(rawPassword)), bytesOf(encodedPassword));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return false;
    }

    private static byte[] bytesOf(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
