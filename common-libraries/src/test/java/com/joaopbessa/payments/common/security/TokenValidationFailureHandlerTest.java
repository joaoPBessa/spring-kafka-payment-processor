package com.joaopbessa.payments.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@DisplayName("TokenValidationFailureHandler")
class TokenValidationFailureHandlerTest {

    private final TokenValidationFailureHandler handler = new TokenValidationFailureHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("Should answer 503 with no body and no challenge when the token could not be checked")
    void shouldAnswer503WithNoBodyAndNoChallengeWhenTheTokenCouldNotBeChecked() throws Exception {
        var failure = new AuthenticationServiceException("An error occurred while attempting to decode the Jwt",
                new ConnectException("Connection refused"));

        handler.onAuthenticationFailure(request, response, failure);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    @DisplayName("Should answer 401 with the invalid_token challenge when the token is invalid")
    void shouldAnswer401WithTheInvalidTokenChallengeWhenTheTokenIsInvalid() throws Exception {
        handler.onAuthenticationFailure(request, response, new InvalidBearerTokenException("The token is expired"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer").contains("invalid_token");
    }

    @Test
    @DisplayName("Should answer 401 to any other authentication failure")
    void shouldAnswer401ToAnyOtherAuthenticationFailure() throws Exception {
        handler.onAuthenticationFailure(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

}
