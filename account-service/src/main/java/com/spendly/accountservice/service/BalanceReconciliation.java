package com.spendly.accountservice.service;

import com.spendly.common.money.Money;

public record BalanceReconciliation(Money cachedBefore, Money replayed, long postings) {

    public Money drift() {
        return cachedBefore.minus(replayed);
    }

    public boolean wasConsistent() {
        return drift().isZero();
    }
}
