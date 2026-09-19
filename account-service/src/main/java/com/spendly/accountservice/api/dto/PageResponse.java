package com.spendly.accountservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "One page of a list endpoint")
public record PageResponse<T>(
        @Schema(requiredMode = RequiredMode.REQUIRED) List<T> content,
        @Schema(description = "Zero-based page index", requiredMode = RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = RequiredMode.REQUIRED) int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
