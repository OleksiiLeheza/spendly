package com.spendly.transactionservice.api.dto;

import com.spendly.common.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "SplitResponse")
public record SplitResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID categoryId,
        @Schema(requiredMode = RequiredMode.REQUIRED) Money amount,
        @Nullable String note) {
}
