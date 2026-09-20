package com.spendly.transactionservice.api.dto;

import com.spendly.common.money.Money;
import com.spendly.transactionservice.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "PostTransactionRequest", description = "Appends one entry to the ledger")
public record PostTransactionRequest(
        @NotNull @Schema(requiredMode = RequiredMode.REQUIRED) UUID accountId,
        @Schema(description = "Receiving account; required for and only for TRANSFER") @Nullable UUID transferAccountId,
        @NotNull @Schema(requiredMode = RequiredMode.REQUIRED) TransactionType type,
        @NotNull @Schema(description = "Positive; the type carries the direction", requiredMode = RequiredMode.REQUIRED) Money amount,
        @NotNull @Schema(example = "2026-09-19", requiredMode = RequiredMode.REQUIRED) LocalDate bookedOn,
        @NotBlank @Size(max = 200) @Schema(example = "Weekly groceries", requiredMode = RequiredMode.REQUIRED) String description,
        @Size(max = 120) @Schema(example = "Silpo") @Nullable String merchant,
        @Size(max = 4000) @Nullable String notes,
        @Size(max = 20) @Nullable List<@NotBlank @Size(max = 40) String> tags,
        @Schema(description = "Category for the whole amount; mutually exclusive with splits") @Nullable UUID categoryId,
        @Size(max = 20) @Nullable List<@Valid SplitRequest> splits) {
}
