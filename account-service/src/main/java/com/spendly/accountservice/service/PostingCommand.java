package com.spendly.accountservice.service;

import com.spendly.common.money.Money;
import java.time.LocalDate;
import java.util.UUID;

public record PostingCommand(UUID ownerId, UUID accountId, UUID ledgerEntryId, Money amount, LocalDate bookedOn) {
}
