package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "UpdateTransactionRequest", description = "Changes the labels on an entry; omitted fields stay as they are. Amount, accounts, type and"
        + " date are immutable - post a reversal instead.")
public record UpdateTransactionRequest(
        @Size(max = 200) @Nullable String description,
        @Size(max = 120) @Nullable String merchant,
        @Size(max = 4000) @Nullable String notes,
        @Size(max = 20) @Nullable List<@NotBlank @Size(max = 40) String> tags,
        @Nullable UUID categoryId,
        @Schema(description = "True removes the category and any splits") @Nullable Boolean clearCategory,
        @Schema(description = "Replaces the splits; an empty list removes them") @Size(max = 20) @Nullable List<@Valid SplitRequest> splits) {
}
