package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

@Schema(name = "CategoryTreeResponse", description = "The caller's whole category tree; roots ordered by sortOrder then name")
public record CategoryTreeResponse(@Schema(requiredMode = RequiredMode.REQUIRED) List<CategoryTreeNode> roots) {

    public CategoryTreeResponse {
        roots = List.copyOf(roots);
    }
}
