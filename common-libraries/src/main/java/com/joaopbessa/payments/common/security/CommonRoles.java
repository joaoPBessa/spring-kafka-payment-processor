package com.joaopbessa.payments.common.security;

/**
 * Roles that every application of the platform understands.
 */
public final class CommonRoles {

    /** Allows reading {@code /actuator/prometheus}. */
    public static final String METRICS_READ = "METRICS_READ";

    private CommonRoles() {
    }

}
