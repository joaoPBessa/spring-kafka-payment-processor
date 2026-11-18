package com.joaopbessa.payments.common.security;

/**
 * Paths every application of the platform answers without credentials.
 */
public final class OpenEndpoints {

    private static final String[] PATHS = { "/actuator/health", "/actuator/health/**", "/error" };

    private OpenEndpoints() {
    }

    /**
     * The health check, and {@code /error}: without it the error dispatch of a 404 or a 400 is answered with a
     * 401 and the response body is lost.
     */
    public static String[] paths() {
        return PATHS.clone();
    }

}
