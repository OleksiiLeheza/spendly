package com.spendly.common.security;

import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The authenticated caller, flattened out of the access token.
 *
 * @param id
 *            the token subject as a UUID - Keycloak issues UUID subjects
 * @param subject
 *            the raw {@code sub} claim
 * @param email
 *            the {@code email} claim, absent when the token carries no email scope
 * @param username
 *            the {@code preferred_username} claim
 * @param roles
 *            realm roles granted to the caller
 */
public record CurrentUserPrincipal(
        UUID id,
        String subject,
        @Nullable String email,
        @Nullable String username,
        Set<String> roles) {

    public CurrentUserPrincipal {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
