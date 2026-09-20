package com.spendly.transactionservice.service;

import com.spendly.common.money.Money;
import com.spendly.transactionservice.domain.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PostTransaction(
        UUID accountId,
        @Nullable UUID transferAccountId,
        TransactionType type,
        Money amount,
        LocalDate bookedOn,
        String description,
        @Nullable String merchant,
        @Nullable String notes,
        List<String> tags,
        @Nullable UUID categoryId,
        List<SplitCommand> splits) {

    public PostTransaction {
        tags = tags == null ? List.of() : List.copyOf(tags);
        splits = splits == null ? List.of() : List.copyOf(splits);
    }
}
