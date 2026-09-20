package com.spendly.transactionservice.service;

import com.spendly.transactionservice.domain.CategoryKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record CategorySeed(String key, String name, CategoryKind kind, @Nullable String icon, List<Child> children) {

    public CategorySeed {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public record Child(String key, String name) {
    }
}
