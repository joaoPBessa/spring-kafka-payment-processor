package com.joaopbessa.payments.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import com.joaopbessa.payments.auth.config.AuthServerProperties.ServiceClient;
import com.joaopbessa.payments.auth.tokens.RolesClaimCustomizer;
import com.joaopbessa.payments.common.security.OpenEndpoints;

/**
 * The OAuth2 authorization server: people sign in with authorization code and PKCE (the login form checks the user
 * tables), services authenticate with client credentials. The signing key is the ephemeral RSA key that Spring Boot
 * generates at startup and publishes at {@code /oauth2/jwks}, so tokens stop being valid when the server restarts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthServerProperties.class)
public class AuthorizationServerConfig {

	static final String PAYMENTS_CLI = "payments-cli";
	static final String PROMETHEUS = "prometheus";
	static final String TRAFFIC_GENERATOR = "traffic-generator";

	private static final String LOGIN_PAGE = "/login";

	@Bean
	@Order(1)
	SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
		http.oauth2AuthorizationServer(server -> http.securityMatcher(server.getEndpointsMatcher()))
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
						new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE),
						new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(OpenEndpoints.health()).permitAll()
						.requestMatchers(OpenEndpoints.ERROR_PATH).permitAll()
						.anyRequest().authenticated())
				.formLogin(Customizer.withDefaults())
				.build();
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(AuthServerProperties properties) {
		return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
	}

	@Bean
	RegisteredClientRepository registeredClientRepository(AuthServerProperties properties, PasswordEncoder encoder) {
		TokenSettings tokens = TokenSettings.builder()
				.accessTokenTimeToLive(properties.accessTokenTimeToLive())
				.build();
		AuthServerProperties.Clients clients = properties.clients();
		return new InMemoryRegisteredClientRepository(
				paymentsCli(clients.paymentsCli(), tokens),
				serviceClient(PROMETHEUS, clients.prometheus(), encoder, tokens),
				serviceClient(TRAFFIC_GENERATOR, clients.trafficGenerator(), encoder, tokens));
	}

	private RegisteredClient paymentsCli(AuthServerProperties.PublicClient client, TokenSettings tokens) {
		return RegisteredClient.withId(PAYMENTS_CLI)
				.clientId(PAYMENTS_CLI)
				.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri(client.redirectUri())
				.clientSettings(ClientSettings.builder()
						.requireProofKey(true)
						.requireAuthorizationConsent(false)
						.build())
				.tokenSettings(tokens)
				.build();
	}

	private RegisteredClient serviceClient(String clientId, ServiceClient client, PasswordEncoder encoder,
			TokenSettings tokens) {
		return RegisteredClient.withId(clientId)
				.clientId(clientId)
				.clientSecret(encoder.encode(client.secret()))
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.clientSettings(ClientSettings.builder()
						.setting(RolesClaimCustomizer.CLIENT_ROLES_SETTING, client.roles())
						.build())
				.tokenSettings(tokens)
				.build();
	}

}
