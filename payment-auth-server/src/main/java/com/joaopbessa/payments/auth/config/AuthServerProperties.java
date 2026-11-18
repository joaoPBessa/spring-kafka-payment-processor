package com.joaopbessa.payments.auth.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Settings of the authorization server. The client secrets are not in any application file: they are read from Vault
 * ({@code secret/payment-auth-server}), and the application refuses to start without them.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthServerProperties(@NotBlank String issuer,
		@NotNull Duration accessTokenTimeToLive,
		@NotNull @Valid Clients clients) {

	public record Clients(@NotNull @Valid PublicClient paymentsCli,
			@NotNull @Valid ServiceClient prometheus,
			@NotNull @Valid ServiceClient trafficGenerator) {
	}

	/** A client that signs people in (authorization code with PKCE); it has no secret to keep. */
	public record PublicClient(@NotBlank String redirectUri) {
	}

	/** A client that acts on its own behalf (client credentials) with a fixed set of roles. */
	public record ServiceClient(@NotBlank String secret, @NotEmpty Set<@NotBlank String> roles) {

		private static final String HIDDEN = "***";

		@Override
		public String toString() {
			return "ServiceClient[secret=" + HIDDEN + ", roles=" + roles + "]";
		}

	}

}
