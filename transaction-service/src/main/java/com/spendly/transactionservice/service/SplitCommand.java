package com.spendly.transactionservice.service;

import com.spendly.common.money.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SplitCommand(UUID categoryId, Money amount, @Nullable String note) {
}
