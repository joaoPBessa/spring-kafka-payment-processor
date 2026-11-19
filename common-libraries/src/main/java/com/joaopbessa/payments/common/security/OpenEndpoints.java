package com.joaopbessa.payments.common.security;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * What every application of the platform answers without credentials.
 */
public final class OpenEndpoints {

    /**
     * The error dispatch: without it a 404 or a 400 is answered with a 401 and the response body is lost.
     */
    public static final String ERROR_PATH = "/error";

    private static final String HEALTH_ENDPOINT_ID = "health";

    private OpenEndpoints() {
    }

    /**
     * The health endpoint and its groups, matched by endpoint id, so a different
     * {@code management.endpoints.web.base-path} or {@code path-mapping} keeps the endpoint open. Needs the Actuator
     * endpoints in the application context; without them it matches nothing. A separate management port is not
     * covered, because that context has its own security.
     */
    public static RequestMatcher health() {
        return EndpointRequest.to(HEALTH_ENDPOINT_ID);
    }

}
