package com.spendly.common.security;

import java.time.Instant;
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
 * @param households
 *            the {@code households} claim: ids of the households the caller belongs to (Keycloak group membership)
 * @param authenticatedAt
 *            the {@code auth_time} claim: when the user last authenticated interactively, absent for a token that
 *            carries none
 */
public record CurrentUserPrincipal(
        UUID id,
        String subject,
        @Nullable String email,
        @Nullable String username,
        Set<String> roles,
        Set<UUID> households,
        @Nullable Instant authenticatedAt) {

    public CurrentUserPrincipal {
        roles = Set.copyOf(roles);
        households = Set.copyOf(households);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isMemberOf(@Nullable UUID householdId) {
        return householdId != null && households.contains(householdId);
    }
}
