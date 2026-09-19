package com.spendly.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A transaction was recorded against an account.
 *
 * <p>
 * The amount travels as a {@link BigDecimal} plus an ISO-4217 code rather than a {@code Money} object: {@code Money}
 * lives in {@code common}, and a contract module must not drag that in.
 */
public record TransactionCreated(
        UUID eventId,
        UUID aggregateId,
        Instant occurredAt,
        UUID accountId,
        UUID ownerId,
        BigDecimal amount,
        String currencyCode,
        LocalDate bookedOn,
        @Nullable UUID categoryId,
        @Nullable String description)
        implements
            DomainEvent {
}
