package com.spendly.transactionservice.domain;

public enum TransactionType {
    INCOME, EXPENSE, TRANSFER;

    public TransactionType reversed() {
        return switch (this) {
            case INCOME -> EXPENSE;
            case EXPENSE -> INCOME;
            case TRANSFER -> TRANSFER;
        };
    }
}
