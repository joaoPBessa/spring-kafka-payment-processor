package com.joaopbessa.payments.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

@DisplayName("TestJwtIssuer")
class TestJwtIssuerTest {

    private final TestJwtIssuer issuer = new TestJwtIssuer();
    private final JwtDecoder decoder = issuer.decoder();

    @Test
    @DisplayName("Should issue tokens that its own decoder accepts, carrying the issuer and the roles claim")
    void shouldIssueTokensThatItsOwnDecoderAcceptsCarryingTheIssuerAndTheRolesClaim() {
        Jwt jwt = decoder.decode(issuer.token("ACCOUNT_READ", "ACCOUNT_WRITE"));

        assertThat(jwt.getIssuer()).hasToString(TestJwtIssuer.ISSUER);
        assertThat(jwt.getClaimAsStringList(TokenClaims.ROLES)).containsExactly("ACCOUNT_READ", "ACCOUNT_WRITE");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should issue a token without roles when none are given")
    void shouldIssueATokenWithoutRolesWhenNoneAreGiven() {
        Jwt jwt = decoder.decode(issuer.token());

        assertThat(jwt.getClaimAsStringList(TokenClaims.ROLES)).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Should issue expired tokens that the decoder rejects")
    void shouldIssueExpiredTokensThatTheDecoderRejects() {
        String expired = issuer.expiredToken("ACCOUNT_READ");

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("Should reject a token signed by another issuer instance")
    void shouldRejectATokenSignedByAnotherIssuerInstance() {
        String foreign = new TestJwtIssuer().token("ACCOUNT_READ");

        assertThatThrownBy(() -> decoder.decode(foreign)).isInstanceOf(BadJwtException.class);
    }

}
