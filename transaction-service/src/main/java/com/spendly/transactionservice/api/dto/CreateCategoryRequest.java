package com.spendly.transactionservice.api.dto;

import com.spendly.transactionservice.domain.CategoryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(name = "CreateCategoryRequest")
public record CreateCategoryRequest(
        @NotBlank @Size(max = 80) @Schema(example = "Coffee", requiredMode = RequiredMode.REQUIRED) String name,
        @NotNull @Schema(requiredMode = RequiredMode.REQUIRED) CategoryKind kind,
        @Size(max = 40) @Schema(description = "Material icon name", example = "local_cafe") @Nullable String icon,
        @Schema(description = "Parent category; omit for a root category") @Nullable UUID parentId) {
}
