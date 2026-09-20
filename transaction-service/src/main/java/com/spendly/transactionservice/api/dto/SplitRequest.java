package com.spendly.transactionservice.api.dto;

import com.spendly.common.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "SplitRequest", description = "One share of a transaction assigned to a category")
public record SplitRequest(
        @NotNull @Schema(requiredMode = RequiredMode.REQUIRED) UUID categoryId,
        @NotNull @Schema(requiredMode = RequiredMode.REQUIRED) Money amount,
        @Size(max = 200) @Nullable String note) {
}
