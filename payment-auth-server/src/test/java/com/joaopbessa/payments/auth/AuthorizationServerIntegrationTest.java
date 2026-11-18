package com.joaopbessa.payments.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.jayway.jsonpath.JsonPath;
import com.joaopbessa.payments.auth.config.AuthServerProperties;
import com.joaopbessa.payments.common.security.TokenClaims;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ PostgresContainerConfiguration.class, AuthDatabaseRoleConfiguration.class })
@DisplayName("Authorization server")
class AuthorizationServerIntegrationTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String AUTHORIZE_ENDPOINT = "/oauth2/authorize";
    private static final String JWKS_ENDPOINT = "/oauth2/jwks";
    private static final String LOGIN_ENDPOINT = "/login";

    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String AUTHORIZATION_CODE = "authorization_code";
    private static final String PAYMENTS_CLI = "payments-cli";
    private static final String PROMETHEUS = "prometheus";
    private static final String TRAFFIC_GENERATOR = "traffic-generator";
    private static final String PROMETHEUS_SECRET = "test-prometheus-secret";
    private static final String TRAFFIC_GENERATOR_SECRET = "test-traffic-generator-secret";
    private static final String CALLBACK = "http://127.0.0.1:8085/callback";
    private static final String STATE = "state-123";

    private static final String DISABLED_USER = "disabled-demo-user";
    private static final String DISABLED_USER_PASSWORD = "disabled-demo-user-demo";
    private static final String BASE64_OF_DISABLED_USER_PASSWORD = "{base64}"
            + Base64.getEncoder().encodeToString(DISABLED_USER_PASSWORD.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthServerProperties properties;

    @Autowired
    private DataSource applicationDataSource;

    @Autowired
    private JdbcTemplate adminJdbcTemplate;

    @AfterEach
    void removeFixtures() {
        adminJdbcTemplate.update("DELETE FROM app_user WHERE username = ?", DISABLED_USER);
    }

    private record Pkce(String verifier, String challenge) {

        static Pkce generate() throws Exception {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return new Pkce(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        }

    }

    private MockHttpServletResponse requestToken(String clientId, String clientSecret) throws Exception {
        return mockMvc.perform(post(TOKEN_ENDPOINT)
                .with(httpBasic(clientId, clientSecret))
                .param("grant_type", CLIENT_CREDENTIALS))
                .andReturn().getResponse();
    }

    private Jwt decodeWithPublishedKey(String accessToken) throws Exception {
        String jwks = mockMvc.perform(get(JWKS_ENDPOINT)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeys().get(0);
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(accessToken);
    }

    private MockHttpServletRequestBuilder authorizeRequest(Pkce pkce, MockHttpSession session) {
        return get(AUTHORIZE_ENDPOINT)
                .session(session)
                .queryParam("response_type", "code")
                .queryParam("client_id", PAYMENTS_CLI)
                .queryParam("redirect_uri", CALLBACK)
                .queryParam("state", STATE)
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256");
    }

    /** Walks the browser steps: authorize, sign in on the login form, and follow the redirect back to authorize. */
    private MockHttpServletResponse signIn(String username, String password, Pkce pkce) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(authorizeRequest(pkce, session)).andExpect(status().isFound());

        MockHttpServletResponse login = mockMvc.perform(post(LOGIN_ENDPOINT)
                .session(session).with(csrf())
                .param("username", username).param("password", password))
                .andReturn().getResponse();
        if (login.getRedirectedUrl() == null || login.getRedirectedUrl().contains("error")) {
            return login;
        }
        return mockMvc.perform(get(login.getRedirectedUrl()).session(session)).andReturn().getResponse();
    }

    private String authorizationCodeOf(MockHttpServletResponse redirect) {
        return UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams().getFirst("code");
    }

    private MockHttpServletResponse exchangeCode(String code, Pkce pkce) throws Exception {
        return mockMvc.perform(post(TOKEN_ENDPOINT)
                .param("grant_type", AUTHORIZATION_CODE)
                .param("client_id", PAYMENTS_CLI)
                .param("redirect_uri", CALLBACK)
                .param("code", code)
                .param("code_verifier", pkce.verifier()))
                .andReturn().getResponse();
    }

    @Test
    @DisplayName("Should issue a signed access token with the configured roles to the Prometheus client")
    void shouldIssueTokenToPrometheus() throws Exception {
        MockHttpServletResponse response = requestToken(PROMETHEUS, PROMETHEUS_SECRET);

        assertThat(response.getStatus()).isEqualTo(200);
        Jwt jwt = decodeWithPublishedKey(JsonPath.read(response.getContentAsString(), "$.access_token"));
        assertThat(jwt.getIssuer()).hasToString("http://localhost:9000");
        assertThat(jwt.getSubject()).isEqualTo(PROMETHEUS);
        assertThat(jwt.getClaimAsStringList(TokenClaims.ROLES)).containsExactly("METRICS_READ");
    }

    @Test
    @DisplayName("Should issue the producer roles to the traffic generator client")
    void shouldIssueProducerRolesToTrafficGenerator() throws Exception {
        MockHttpServletResponse response = requestToken(TRAFFIC_GENERATOR, TRAFFIC_GENERATOR_SECRET);

        Jwt jwt = decodeWithPublishedKey(JsonPath.read(response.getContentAsString(), "$.access_token"));
        assertThat(jwt.getClaimAsStringList(TokenClaims.ROLES))
                .containsExactlyInAnyOrder("ACCOUNT_READ", "ACCOUNT_WRITE", "PAYMENT_CREATE");
    }

    @Test
    @DisplayName("Should expire a token after the configured lifetime")
    void shouldExpireTokenAfterTheConfiguredLifetime() throws Exception {
        MockHttpServletResponse response = requestToken(PROMETHEUS, PROMETHEUS_SECRET);

        Jwt jwt = decodeWithPublishedKey(JsonPath.read(response.getContentAsString(), "$.access_token"));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(properties.accessTokenTimeToLive());
    }

    @Test
    @DisplayName("Should not issue a refresh token")
    void shouldNotIssueRefreshToken() throws Exception {
        MockHttpServletResponse response = requestToken(PROMETHEUS, PROMETHEUS_SECRET);

        assertThat(response.getContentAsString()).doesNotContain("refresh_token");
    }

    @Test
    @DisplayName("Should reject a client that sends the wrong secret")
    void shouldRejectWrongClientSecret() throws Exception {
        assertThat(requestToken(PROMETHEUS, "not-the-secret").getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should reject an unknown client")
    void shouldRejectUnknownClient() throws Exception {
        assertThat(requestToken("unknown-client", PROMETHEUS_SECRET).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should not let a service client use the secret of another one")
    void shouldNotAcceptTheSecretOfAnotherClient() throws Exception {
        assertThat(requestToken(TRAFFIC_GENERATOR, PROMETHEUS_SECRET).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should send an unauthenticated browser to the login page")
    void shouldSendUnauthenticatedBrowserToLogin() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(authorizeRequest(Pkce.generate(), new MockHttpSession())
                .accept("text/html")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).endsWith(LOGIN_ENDPOINT);
    }

    @Test
    @DisplayName("Should complete authorization code with PKCE and put the roles stored for the user in the token")
    void shouldCompleteAuthorizationCodeWithPkce() throws Exception {
        Pkce pkce = Pkce.generate();

        MockHttpServletResponse redirect = signIn("dlt-operator", "dlt-operator-demo", pkce);

        assertThat(redirect.getRedirectedUrl()).startsWith(CALLBACK);
        assertThat(UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams()
                .getFirst("state")).isEqualTo(STATE);
        MockHttpServletResponse token = exchangeCode(authorizationCodeOf(redirect), pkce);
        assertThat(token.getStatus()).isEqualTo(200);
        Jwt jwt = decodeWithPublishedKey(JsonPath.read(token.getContentAsString(), "$.access_token"));
        assertThat(jwt.getSubject()).isEqualTo("dlt-operator");
        assertThat(jwt.getClaimAsStringList(TokenClaims.ROLES)).containsExactlyInAnyOrder("DLT_VIEW", "DLT_REPLAY");
    }

    @Test
    @DisplayName("Should give a user with a single role only that role")
    void shouldGiveReadOnlyUserOnlyTheReadRole() throws Exception {
        Pkce pkce = Pkce.generate();

        MockHttpServletResponse redirect = signIn("dlt-viewer", "dlt-viewer-demo", pkce);

        Jwt jwt = decodeWithPublishedKey(
                JsonPath.read(exchangeCode(authorizationCodeOf(redirect), pkce).getContentAsString(), "$.access_token"));
        assertThat(jwt.getClaimAsStringList(TokenClaims.ROLES)).containsExactly("DLT_VIEW");
    }

    @Test
    @DisplayName("Should refuse the code exchange when the PKCE verifier does not match")
    void shouldRefuseCodeExchangeWithWrongVerifier() throws Exception {
        Pkce pkce = Pkce.generate();
        MockHttpServletResponse redirect = signIn("dlt-viewer", "dlt-viewer-demo", pkce);

        MockHttpServletResponse token = exchangeCode(authorizationCodeOf(redirect), Pkce.generate());

        assertThat(token.getStatus()).isEqualTo(400);
        assertThat(token.getContentAsString()).doesNotContain("access_token");
    }

    @Test
    @DisplayName("Should refuse an authorization request that has no PKCE challenge")
    void shouldRefuseAuthorizationRequestWithoutPkce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(authorizeRequest(Pkce.generate(), session));
        MockHttpServletResponse login = mockMvc.perform(post(LOGIN_ENDPOINT).session(session).with(csrf())
                .param("username", "dlt-viewer").param("password", "dlt-viewer-demo")).andReturn().getResponse();

        MockHttpServletResponse response = mockMvc.perform(get(AUTHORIZE_ENDPOINT).session(session)
                .queryParam("response_type", "code")
                .queryParam("client_id", PAYMENTS_CLI)
                .queryParam("redirect_uri", CALLBACK)).andReturn().getResponse();

        assertThat(login.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).doesNotContain("code=");
    }

    @Test
    @DisplayName("Should keep the user on the login page when the password is wrong")
    void shouldRejectWrongPassword() throws Exception {
        MockHttpServletResponse response = signIn("dlt-operator", "wrong-password", Pkce.generate());

        assertThat(response.getRedirectedUrl()).contains("error");
    }

    @Test
    @DisplayName("Should keep an unknown user on the login page")
    void shouldRejectUnknownUser() throws Exception {
        MockHttpServletResponse response = signIn("nobody", "nobody-demo", Pkce.generate());

        assertThat(response.getRedirectedUrl()).contains("error");
    }

    @Test
    @DisplayName("Should keep a disabled user on the login page even with the right password")
    void shouldRejectDisabledUser() throws Exception {
        adminJdbcTemplate.update("INSERT INTO app_user (id, username, password, enabled) VALUES (?, ?, ?, FALSE)",
                UUID.randomUUID(), DISABLED_USER, BASE64_OF_DISABLED_USER_PASSWORD);

        MockHttpServletResponse response = signIn(DISABLED_USER, DISABLED_USER_PASSWORD, Pkce.generate());

        assertThat(response.getRedirectedUrl()).contains("error");
    }

    @Test
    @DisplayName("Should publish only the public part of the signing key")
    void shouldPublishOnlyThePublicKey() throws Exception {
        String jwks = mockMvc.perform(get(JWKS_ENDPOINT)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> keyTypes = JsonPath.read(jwks, "$.keys[*].kty");
        assertThat(keyTypes).containsExactly("RSA");
        assertThat(jwks).contains("\"n\"", "\"e\"", "\"kid\"")
                .doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
    }

    @Test
    @DisplayName("Should publish the server metadata with the configured issuer, without the password grant")
    void shouldPublishServerMetadata() throws Exception {
        String metadata = mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(metadata, "$.issuer")).isEqualTo("http://localhost:9000");
        assertThat((String) JsonPath.read(metadata, "$.jwks_uri")).endsWith(JWKS_ENDPOINT);
        List<String> grants = JsonPath.read(metadata, "$.grant_types_supported");
        assertThat(grants).contains(AUTHORIZATION_CODE, CLIENT_CREDENTIALS).doesNotContain("password");
        List<String> methods = JsonPath.read(metadata, "$.code_challenge_methods_supported");
        assertThat(methods).containsExactly("S256");
    }

    @Test
    @DisplayName("Should answer the health check without credentials")
    void shouldAnswerHealthWithoutCredentials() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should not expose the Actuator endpoints that print configuration")
    void shouldNotExposeConfigurationEndpoints() throws Exception {
        for (String endpoint : List.of("/actuator/env", "/actuator/configprops", "/actuator/beans")) {
            assertThat(mockMvc.perform(get(endpoint)).andReturn().getResponse().getStatus())
                    .as(endpoint).isNotEqualTo(200);
        }
    }

    @Test
    @DisplayName("Should run as a database role that cannot write the user tables")
    void shouldRunAsReadOnlyDatabaseRole() {
        JdbcTemplate applicationJdbc = new JdbcTemplate(applicationDataSource);

        assertThat(applicationJdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("payment-auth-app");
        assertThatThrownBy(() -> applicationJdbc.update(
                "INSERT INTO app_user (id, username, password) VALUES (?, ?, ?)",
                UUID.randomUUID(), "intruder", "{base64}c2VjcmV0"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied for table app_user");
    }

}
