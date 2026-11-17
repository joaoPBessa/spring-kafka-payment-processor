package com.joaopbessa.payments.common.security;

/**
 * Names of the JWT claims shared by the module that issues the tokens and the applications that validate them.
 */
public final class TokenClaims {

    /** Claim with the list of role names granted to the token (without the {@code ROLE_} prefix). */
    public static final String ROLES = "roles";

    private TokenClaims() {
    }

}
