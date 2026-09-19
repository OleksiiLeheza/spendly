package com.spendly.accountservice.service;

import com.spendly.accountservice.domain.AccountType;

public record OpenAccount(String name, AccountType type, String currencyCode) {
}
