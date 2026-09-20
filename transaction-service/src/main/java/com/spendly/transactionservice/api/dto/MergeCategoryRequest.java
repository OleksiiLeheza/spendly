package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(name = "MergeCategoryRequest", description = "Moves every use of this category to the target and archives it")
public record MergeCategoryRequest(@NotNull @Schema(requiredMode = RequiredMode.REQUIRED) UUID targetId) {
}
