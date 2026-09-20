package com.spendly.transactionservice.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record FeedPage(List<TransactionRow> items, @Nullable FeedCursor next) {

    public FeedPage {
        items = List.copyOf(items);
    }
}
