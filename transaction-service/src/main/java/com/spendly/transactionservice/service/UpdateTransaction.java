package com.spendly.transactionservice.service;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UpdateTransaction(
        @Nullable String description,
        @Nullable String merchant,
        @Nullable String notes,
        @Nullable List<String> tags,
        @Nullable UUID categoryId,
        @Nullable Boolean clearCategory,
        @Nullable List<SplitCommand> splits) {

    public UpdateTransaction {
        tags = tags == null ? null : List.copyOf(tags);
        splits = splits == null ? null : List.copyOf(splits);
    }

    public boolean clearsCategory() {
        return Boolean.TRUE.equals(clearCategory);
    }
}
