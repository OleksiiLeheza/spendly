package com.spendly.transactionservice.api.dto;

import com.spendly.transactionservice.domain.CategoryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "CategoryResponse")
public record CategoryResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = RequiredMode.REQUIRED) CategoryKind kind,
        @Nullable String icon,
        @Nullable UUID parentId,
        @Schema(requiredMode = RequiredMode.REQUIRED) int sortOrder,
        @Schema(description = "Set for a seeded default, e.g. food.groceries") @Nullable String seedKey,
        @Nullable Instant archivedAt,
        @Schema(description = "Set when the category was merged away") @Nullable UUID mergedIntoId,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {
}
