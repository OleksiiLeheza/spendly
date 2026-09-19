package com.spendly.events;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A user completed registration, whether by password or through an identity provider.
 *
 * @param identityProvider
 *            the IdP alias when the account came from social login (e.g. {@code google}), null for a local registration
 */
public record UserRegistered(
        UUID eventId,
        UUID aggregateId,
        Instant occurredAt,
        String email,
        @Nullable String displayName,
        @Nullable String identityProvider)
        implements
            DomainEvent {

    public static UserRegistered now(
            UUID userId, String email, @Nullable String displayName, @Nullable String identityProvider) {
        return new UserRegistered(
                UUID.randomUUID(), userId, Instant.now(), email, displayName, identityProvider);
    }
}
