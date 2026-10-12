package com.spendly.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
        @Nullable String description,
        String type,
        @Nullable UUID transferAccountId,
        @Nullable UUID reversesTransactionId,
        List<Allocation> allocations,
        @Nullable String idempotencyKey,
        @Nullable String merchant)
        implements
            DomainEvent {

    public TransactionCreated {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }
}
