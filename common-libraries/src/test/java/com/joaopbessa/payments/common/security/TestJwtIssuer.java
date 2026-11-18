package com.joaopbessa.payments.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Test support: issues signed JWTs with the claims the platform's resource servers expect, and gives the matching
 * {@link JwtDecoder}, so tests that boot a full context can authenticate without running the token issuer. Every
 * instance has its own key pair, so a token from one instance is rejected by the decoder of another.
 */
public final class TestJwtIssuer {

    public static final String ISSUER = "http://test-issuer.local";

    private static final Duration VALIDITY = Duration.ofMinutes(5);

    private final RSAKey key;
    private final JwtEncoder encoder;

    public TestJwtIssuer() {
        try {
            this.key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate the test signing key", e);
        }
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    public JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not read the test public key", e);
        }
    }

    public String token(String... roles) {
        return tokenIssuedAt(Instant.now(), roles);
    }

    /** The value of an {@code Authorization} header that presents a token with the given roles. */
    public String bearer(String... roles) {
        return "Bearer " + token(roles);
    }

    public String expiredToken(String... roles) {
        return tokenIssuedAt(Instant.now().minus(Duration.ofHours(1)), roles);
    }

    private String tokenIssuedAt(Instant issuedAt, String... roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("test-subject")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(VALIDITY))
                .claim(TokenClaims.ROLES, List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

}
