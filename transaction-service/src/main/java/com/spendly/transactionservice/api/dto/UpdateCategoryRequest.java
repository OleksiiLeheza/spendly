package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "UpdateCategoryRequest", description = "Partial update; omitted fields are left unchanged")
public record UpdateCategoryRequest(
        @Size(max = 80) @Nullable String name,
        @Size(max = 40) @Nullable String icon,
        @Schema(description = "New parent; mutually exclusive with moveToRoot") @Nullable UUID parentId,
        @Schema(description = "True makes this a root category") @Nullable Boolean moveToRoot,
        @Nullable Integer sortOrder) {
}
