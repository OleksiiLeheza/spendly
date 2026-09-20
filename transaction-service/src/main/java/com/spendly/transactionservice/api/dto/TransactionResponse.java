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

@Schema(name = "TransactionResponse", description = "One ledger entry")
public record TransactionResponse(
        @Schema(description = "Entry id (UUIDv7)", requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID accountId,
        @Nullable UUID transferAccountId,
        @Schema(requiredMode = RequiredMode.REQUIRED) TransactionType type,
        @Schema(description = "Always positive; see type", requiredMode = RequiredMode.REQUIRED) Money amount,
        @Schema(description = "Negative for EXPENSE and TRANSFER (outflow from accountId)", requiredMode = RequiredMode.REQUIRED) Money signedAmount,
        @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate bookedOn,
        @Schema(requiredMode = RequiredMode.REQUIRED) String description,
        @Nullable String merchant,
        @Nullable String notes,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<String> tags,
        @Nullable UUID categoryId,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<SplitResponse> splits,
        @Schema(description = "Set when this entry reverses another") @Nullable UUID reversesTransactionId,
        @Schema(description = "Set once this entry has been reversed") @Nullable UUID reversedByTransactionId,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant lastModifiedAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {
}
