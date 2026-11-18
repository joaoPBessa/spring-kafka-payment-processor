package com.joaopbessa.payments.producer.security;

/**
 * The roles that grant access to the producer endpoints, and the {@code @PreAuthorize} expressions that require them.
 * The roles come from the {@code roles} claim of the access token; there is no hierarchy, so each endpoint needs
 * exactly the role it names.
 */
public final class Roles {

	public static final String ACCOUNT_READ = "ACCOUNT_READ";
	public static final String ACCOUNT_WRITE = "ACCOUNT_WRITE";
	public static final String PAYMENT_CREATE = "PAYMENT_CREATE";

	public static final String REQUIRES_ACCOUNT_READ = "hasRole('" + ACCOUNT_READ + "')";
	public static final String REQUIRES_ACCOUNT_WRITE = "hasRole('" + ACCOUNT_WRITE + "')";
	public static final String REQUIRES_PAYMENT_CREATE = "hasRole('" + PAYMENT_CREATE + "')";

	private Roles() {
	}

}
