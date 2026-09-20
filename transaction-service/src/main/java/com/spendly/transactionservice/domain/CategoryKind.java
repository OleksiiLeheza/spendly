package com.spendly.transactionservice.domain;

public enum CategoryKind {
    INCOME, EXPENSE;

    public static CategoryKind forTransaction(TransactionType type) {
        return switch (type) {
            case INCOME -> INCOME;
            case EXPENSE -> EXPENSE;
            case TRANSFER -> throw new IllegalArgumentException("A transfer is not categorised");
        };
    }
}
