package com.spendly.transactionservice.service;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UpdateCategory(
        @Nullable String name,
        @Nullable String icon,
        @Nullable UUID parentId,
        @Nullable Boolean moveToRoot,
        @Nullable Integer sortOrder) {

    public boolean movesToRoot() {
        return Boolean.TRUE.equals(moveToRoot);
    }
}
