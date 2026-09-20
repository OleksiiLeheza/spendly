package com.spendly.transactionservice.domain;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FeedQuery(
        UUID ownerId,
        @Nullable UUID accountId,
        @Nullable UUID categoryId,
        @Nullable TransactionType type,
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable String search,
        @Nullable FeedCursor after,
        int limit) {
}
