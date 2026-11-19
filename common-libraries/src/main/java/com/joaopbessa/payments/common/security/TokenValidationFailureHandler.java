package com.joaopbessa.payments.common.security;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * What the resource server answers when a Bearer token is rejected.
 *
 * <p>A token that is invalid, expired or from another issuer gets the usual {@code 401} with a Bearer challenge. A token
 * that <em>could not be checked</em>, because the signing keys of the issuer cannot be fetched, is not the caller's
 * fault: Spring Security reports it as an {@link AuthenticationServiceException} and, left alone, rethrows it as a
 * {@code 500}. It is answered with {@code 503} instead, without a body, and the cause goes to the log.
 *
 * <p>The {@code 401} branch is the default of the resource server configuration: a
 * {@link BearerTokenAuthenticationEntryPoint}, which the configuration does not customize.
 */
class TokenValidationFailureHandler implements AuthenticationFailureHandler {

    private static final Log log = LogFactory.getLog(TokenValidationFailureHandler.class);

    private final AuthenticationFailureHandler unauthorized =
            new AuthenticationEntryPointFailureHandler(new BearerTokenAuthenticationEntryPoint());

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof AuthenticationServiceException) {
            log.warn("A token could not be validated, answering 503: "
                    + NestedExceptionUtils.getMostSpecificCause(exception).getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        unauthorized.onAuthenticationFailure(request, response, exception);
    }

}
