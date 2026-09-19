package com.spendly.accountservice.api.dto;

import com.spendly.common.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(name = "BalanceReconciliationResponse", description = "Result of replaying the account's postings against its cached balance")
public record BalanceReconciliationResponse(
        @Schema(description = "Cached balance before the replay", requiredMode = RequiredMode.REQUIRED) Money cachedBefore,
        @Schema(description = "Balance recomputed from every posting", requiredMode = RequiredMode.REQUIRED) Money replayed,
        @Schema(description = "cachedBefore minus replayed; zero when consistent", requiredMode = RequiredMode.REQUIRED) Money drift,
        @Schema(description = "Number of postings replayed", requiredMode = RequiredMode.REQUIRED) long postings,
        @Schema(description = "True when the cache already matched the ledger", requiredMode = RequiredMode.REQUIRED) boolean wasConsistent) {
}
