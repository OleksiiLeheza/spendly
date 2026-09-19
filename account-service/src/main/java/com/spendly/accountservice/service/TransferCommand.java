package com.spendly.accountservice.service;

import com.spendly.common.money.Money;
import java.time.LocalDate;
import java.util.UUID;

public record TransferCommand(
        UUID ownerId, UUID transferId, UUID fromAccountId, UUID toAccountId, Money amount, LocalDate bookedOn) {
}
