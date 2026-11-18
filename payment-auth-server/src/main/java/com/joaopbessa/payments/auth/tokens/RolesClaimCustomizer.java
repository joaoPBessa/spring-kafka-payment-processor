package com.joaopbessa.payments.auth.tokens;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import com.joaopbessa.payments.common.security.TokenClaims;

/**
 * Writes the {@link TokenClaims#ROLES} claim of every access token, which is what the resource servers turn into
 * authorities. A person gets the roles stored for the user; a service (client credentials) gets the roles configured
 * on its client registration, because it has no user.
 */
@Component
public class RolesClaimCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

	/** Client setting that holds the roles of a service client. */
	public static final String CLIENT_ROLES_SETTING = "settings.client.roles";

	@Override
	public void customize(JwtEncodingContext context) {
		if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
			context.getClaims().claim(TokenClaims.ROLES, rolesOf(context));
		}
	}

	private List<String> rolesOf(JwtEncodingContext context) {
		if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
			return sorted(clientRoles(context));
		}
		return sorted(userRoles(context));
	}

	private Set<String> clientRoles(JwtEncodingContext context) {
		Set<String> roles = context.getRegisteredClient().getClientSettings().getSetting(CLIENT_ROLES_SETTING);
		return roles == null ? Set.of() : roles;
	}

	private Set<String> userRoles(JwtEncodingContext context) {
		return context.getPrincipal().getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(authority -> authority.startsWith(TokenClaims.AUTHORITY_PREFIX))
				.map(authority -> authority.substring(TokenClaims.AUTHORITY_PREFIX.length()))
				.collect(Collectors.toSet());
	}

	private List<String> sorted(Set<String> roles) {
		return roles.stream().sorted().toList();
	}

}
