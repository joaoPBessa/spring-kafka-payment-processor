package com.joaopbessa.payments.auth.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.COLLECTION;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import com.joaopbessa.payments.common.security.TokenClaims;

@DisplayName("RolesClaimCustomizer")
class RolesClaimCustomizerTest {

    private static final OAuth2TokenType ID_TOKEN = new OAuth2TokenType(OidcParameterNames.ID_TOKEN);

    private final RolesClaimCustomizer customizer = new RolesClaimCustomizer();

    private RegisteredClient authorizationCodeClient() {
        return RegisteredClient.withId("1")
                .clientId("payments-cli")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1:8085/callback")
                .build();
    }

    private RegisteredClient serviceClient(Set<String> roles) {
        return RegisteredClient.withId("2")
                .clientId("traffic-generator")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder().setting(RolesClaimCustomizer.CLIENT_ROLES_SETTING, roles).build())
                .build();
    }

    private Map<String, Object> customized(RegisteredClient client, Authentication principal,
            AuthorizationGrantType grantType, OAuth2TokenType tokenType) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("subject");
        JwtEncodingContext context = JwtEncodingContext.with(JwsHeader.with(
                SignatureAlgorithm.RS256), claims)
                .registeredClient(client)
                .principal(principal)
                .authorizationGrantType(grantType)
                .tokenType(tokenType)
                .build();

        customizer.customize(context);

        return claims.build().getClaims();
    }

    private Authentication userWithAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        return UsernamePasswordAuthenticationToken.authenticated("someone", null, granted);
    }

    @Test
    @DisplayName("Should copy the roles of the signed-in user into the access token without the ROLE_ prefix")
    void shouldCopyUserRolesWithoutThePrefix() {
        Map<String, Object> claims = customized(authorizationCodeClient(),
                userWithAuthorities("ROLE_ACCOUNT_READ", "ROLE_ACCOUNT_WRITE"),
                AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);

        assertThat(claims.get(TokenClaims.ROLES)).asInstanceOf(COLLECTION)
                .containsExactlyInAnyOrder("ACCOUNT_READ", "ACCOUNT_WRITE");
    }

    @Test
    @DisplayName("Should ignore authorities that are not roles, such as the factor Spring Security adds at login")
    void shouldIgnoreAuthoritiesThatAreNotRoles() {
        Map<String, Object> claims = customized(authorizationCodeClient(),
                userWithAuthorities("FACTOR_PASSWORD", "ROLE_DLT_VIEW"),
                AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);

        assertThat(claims.get(TokenClaims.ROLES)).asInstanceOf(COLLECTION)
                .containsExactly("DLT_VIEW");
    }

    @Test
    @DisplayName("Should give a user without roles an empty roles claim")
    void shouldGiveUserWithoutRolesAnEmptyClaim() {
        Map<String, Object> claims = customized(authorizationCodeClient(), userWithAuthorities(),
                AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);

        assertThat(claims.get(TokenClaims.ROLES)).asInstanceOf(COLLECTION)
                .isEmpty();
    }

    @Test
    @DisplayName("Should take the roles of a service from its client registration, not from the client authentication")
    void shouldTakeServiceRolesFromTheClientRegistration() {
        Map<String, Object> claims = customized(serviceClient(Set.of("METRICS_READ")),
                userWithAuthorities("ROLE_SOMETHING_ELSE"),
                AuthorizationGrantType.CLIENT_CREDENTIALS, OAuth2TokenType.ACCESS_TOKEN);

        assertThat(claims.get(TokenClaims.ROLES)).asInstanceOf(COLLECTION)
                .containsExactly("METRICS_READ");
    }

    @Test
    @DisplayName("Should give a service registered without roles an empty roles claim")
    void shouldGiveServiceWithoutRolesAnEmptyClaim() {
        Map<String, Object> claims = customized(serviceClient(Set.of()), userWithAuthorities(),
                AuthorizationGrantType.CLIENT_CREDENTIALS, OAuth2TokenType.ACCESS_TOKEN);

        assertThat(claims.get(TokenClaims.ROLES)).asInstanceOf(COLLECTION)
                .isEmpty();
    }

    @Test
    @DisplayName("Should leave the ID token without a roles claim")
    void shouldLeaveTheIdTokenWithoutRoles() {
        Map<String, Object> claims = customized(authorizationCodeClient(),
                userWithAuthorities("ROLE_ACCOUNT_READ"),
                AuthorizationGrantType.AUTHORIZATION_CODE, ID_TOKEN);

        assertThat(claims).doesNotContainKey(TokenClaims.ROLES);
    }

}
