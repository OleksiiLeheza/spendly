package com.spendly.transactionservice.domain;

import com.spendly.common.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TransactionRow(
        UUID id,
        UUID accountId,
        @Nullable UUID transferAccountId,
        TransactionType type,
        Money amount,
        LocalDate bookedOn,
        String description,
        @Nullable String merchant,
        List<String> tags,
        @Nullable UUID categoryId,
        boolean split,
        @Nullable UUID reversesTransactionId,
        @Nullable UUID reversedByTransactionId,
        Instant createdAt) {
}
