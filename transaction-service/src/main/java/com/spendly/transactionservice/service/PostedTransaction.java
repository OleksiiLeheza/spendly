package com.spendly.transactionservice.service;

import com.spendly.transactionservice.domain.Transaction;

public record PostedTransaction(Transaction transaction, boolean replayed) {
}
