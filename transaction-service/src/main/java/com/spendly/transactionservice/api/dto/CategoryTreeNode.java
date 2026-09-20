package com.spendly.transactionservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

@Schema(name = "CategoryTreeNode", description = "A category with its children, nested")
public record CategoryTreeNode(
        @Schema(requiredMode = RequiredMode.REQUIRED) CategoryResponse category,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<CategoryTreeNode> children) {

    public CategoryTreeNode {
        children = List.copyOf(children);
    }
}
