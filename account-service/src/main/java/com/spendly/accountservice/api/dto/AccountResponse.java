package com.spendly.accountservice.api.dto;

import com.spendly.accountservice.domain.AccountType;
import com.spendly.common.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "AccountResponse", description = "An account and its ledger-derived balance")
public record AccountResponse(
        @Schema(description = "Account id (UUIDv7)", requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(example = "Main checking", requiredMode = RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = RequiredMode.REQUIRED) AccountType type,
        @Schema(description = "ISO-4217 code", example = "EUR", requiredMode = RequiredMode.REQUIRED) String currency,
        @Schema(description = "Running balance derived from the ledger", requiredMode = RequiredMode.REQUIRED) Money balance,
        @Schema(description = "Set once the account is archived; archived accounts take no new postings") @Nullable Instant archivedAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {
}
