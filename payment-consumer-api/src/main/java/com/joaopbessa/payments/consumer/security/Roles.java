package com.joaopbessa.payments.consumer.security;

/**
 * The roles that grant access to the dead letter endpoints, and the {@code @PreAuthorize} expressions that require
 * them. The roles come from the {@code roles} claim of the access token; there is no hierarchy, so replaying needs
 * {@link #DLT_REPLAY} even for someone who may view.
 */
public final class Roles {

	public static final String DLT_VIEW = "DLT_VIEW";
	public static final String DLT_REPLAY = "DLT_REPLAY";

	public static final String REQUIRES_DLT_VIEW = "hasRole('" + DLT_VIEW + "')";
	public static final String REQUIRES_DLT_REPLAY = "hasRole('" + DLT_REPLAY + "')";

	private Roles() {
	}

}
