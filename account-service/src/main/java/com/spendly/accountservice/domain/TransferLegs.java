package com.spendly.accountservice.domain;

import com.spendly.common.web.ApiException;
import java.util.Objects;

public record TransferLegs(Posting debit, Posting credit) {

    public TransferLegs {
        Objects.requireNonNull(debit, "debit");
        Objects.requireNonNull(credit, "credit");
        if (debit.getAccount().equals(credit.getAccount())) {
            throw new ApiException.Unprocessable("A transfer needs two different accounts");
        }
        if (!debit.getOwnerId().equals(credit.getOwnerId())) {
            throw new ApiException.Unprocessable("A transfer pair must stay within one owner");
        }
        if (!debit.getLedgerEntryId().equals(credit.getLedgerEntryId())) {
            throw new ApiException.Unprocessable("Both legs of a transfer must reference the same ledger entry");
        }
        if (!debit.getAmount().isNegative() || !credit.getAmount().isPositive()) {
            throw new ApiException.Unprocessable("A transfer debits one account and credits the other");
        }
        if (!debit.getAmount().plus(credit.getAmount()).isZero()) {
            throw new ApiException.Unprocessable("A transfer between own accounts must net to zero");
        }
    }
}
