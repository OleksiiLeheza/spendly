package com.spendly.transactionservice.service;

import com.spendly.transactionservice.domain.CategoryKind;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CreateCategory(String name, CategoryKind kind, @Nullable String icon, @Nullable UUID parentId) {
}
