package com.spendly.transactionservice.api.dto;

import com.spendly.common.money.Money;
import com.spendly.transactionservice.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "TransactionFeedItem", description = "A ledger entry as listed in the feed; fetch the entry for notes and splits")
public record TransactionFeedItem(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID accountId,
        @Nullable UUID transferAccountId,
        @Schema(requiredMode = RequiredMode.REQUIRED) TransactionType type,
        @Schema(requiredMode = RequiredMode.REQUIRED) Money amount,
        @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate bookedOn,
        @Schema(requiredMode = RequiredMode.REQUIRED) String description,
        @Nullable String merchant,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<String> tags,
        @Nullable UUID categoryId,
        @Schema(description = "True when the entry is split across categories", requiredMode = RequiredMode.REQUIRED) boolean split,
        @Nullable UUID reversesTransactionId,
        @Nullable UUID reversedByTransactionId,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt) {
}
