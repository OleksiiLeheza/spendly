package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

@Schema(name = "CategoryMergeResponse")
public record CategoryMergeResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID sourceId,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID targetId,
        @Schema(requiredMode = RequiredMode.REQUIRED) int transactionsMoved,
        @Schema(requiredMode = RequiredMode.REQUIRED) int splitsMoved,
        @Schema(requiredMode = RequiredMode.REQUIRED) int childrenMoved) {
}
