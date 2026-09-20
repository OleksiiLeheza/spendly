package com.spendly.transactionservice.domain;

import com.spendly.common.money.CurrencyMismatchException;
import com.spendly.common.money.Money;
import com.spendly.common.persistence.BaseEntityAudit;
import com.spendly.common.web.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Getter
@Table(name = "transactions")
public class Transaction extends BaseEntityAudit {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "transfer_account_id", updatable = false)
    private @Nullable UUID transferAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 10)
    private TransactionType type;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount_value", nullable = false, updatable = false, precision = 19, scale = 4))
    @AttributeOverride(name = "currency", column = @Column(name = "amount_currency", nullable = false, updatable = false, columnDefinition = "char(3)"))
    private Money amount;

    @Column(name = "booked_on", nullable = false, updatable = false)
    private LocalDate bookedOn;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "merchant", length = 120)
    private @Nullable String merchant;

    @Column(name = "notes")
    private @Nullable String notes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "category_id")
    private @Nullable UUID categoryId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "transaction_splits", joinColumns = @JoinColumn(name = "transaction_id"))
    @OrderColumn(name = "position")
    private List<Split> splits = new ArrayList<>();

    @Column(name = "reverses_transaction_id", updatable = false)
    private @Nullable UUID reversesTransactionId;

    @Column(name = "reversed_by_transaction_id")
    private @Nullable UUID reversedByTransactionId;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private @Nullable String idempotencyKey;

    public static Transaction post(
            UUID ownerId,
            UUID accountId,
            @Nullable UUID transferAccountId,
            TransactionType type,
            Money amount,
            LocalDate bookedOn,
            String description,
            @Nullable String idempotencyKey) {
        if (!amount.isPositive()) {
            throw new ApiException.Unprocessable("The amount must be positive; the type carries the direction");
        }
        if ((type == TransactionType.TRANSFER) != (transferAccountId != null)) {
            throw new ApiException.Unprocessable("A transfer names the receiving account; other types do not");
        }
        if (transferAccountId != null && transferAccountId.equals(accountId)) {
            throw new ApiException.Unprocessable("A transfer needs two different accounts");
        }
        Transaction transaction = new Transaction();
        transaction.ownerId = ownerId;
        transaction.accountId = accountId;
        transaction.transferAccountId = transferAccountId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.bookedOn = bookedOn;
        transaction.description = description;
        transaction.idempotencyKey = idempotencyKey;
        return transaction;
    }

    public Transaction reversal(String description, @Nullable String notes) {
        if (reversedByTransactionId != null) {
            throw new ApiException.Conflict(
                    "Transaction " + getId() + " is already reversed by " + reversedByTransactionId);
        }
        if (reversesTransactionId != null) {
            throw new ApiException.Unprocessable("A reversal cannot itself be reversed; post the original again");
        }
        Transaction reversal = post(
                ownerId,
                type == TransactionType.TRANSFER ? requireTransferAccount() : accountId,
                type == TransactionType.TRANSFER ? accountId : null,
                type.reversed(),
                amount,
                bookedOn,
                description,
                null);
        reversal.notes = notes;
        reversal.merchant = merchant;
        reversal.categoryId = categoryId;
        reversal.tags = new ArrayList<>(tags);
        reversal.splits = new ArrayList<>(splits);
        reversal.reversesTransactionId = getId();
        return reversal;
    }

    public void markReversedBy(Transaction reversal) {
        reversedByTransactionId = reversal.getId();
    }

    public Money signedAmount() {
        return type == TransactionType.INCOME ? amount : amount.negated();
    }

    public boolean isReversed() {
        return reversedByTransactionId != null;
    }

    public boolean isTransfer() {
        return type == TransactionType.TRANSFER;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void setMerchant(@Nullable String merchant) {
        this.merchant = merchant;
    }

    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

    public void setTags(List<String> tags) {
        this.tags = new ArrayList<>(tags);
    }

    public void categorise(@Nullable UUID categoryId) {
        if (isTransfer() && categoryId != null) {
            throw new ApiException.Unprocessable("A transfer is not categorised");
        }
        this.categoryId = categoryId;
        this.splits.clear();
    }

    public void split(List<Split> splits) {
        if (isTransfer()) {
            throw new ApiException.Unprocessable("A transfer is not categorised");
        }
        if (splits.isEmpty()) {
            this.splits.clear();
            return;
        }
        Money total;
        try {
            total = splits.stream().map(Split::amount).reduce(Money.zero(amount.currency()), Money::plus);
        } catch (CurrencyMismatchException ex) {
            throw new ApiException.Unprocessable("Every split must be in " + amount.currency());
        }
        if (!total.equals(amount)) {
            throw new ApiException.Unprocessable(
                    "Splits sum to " + total + " but the transaction amount is " + amount);
        }
        if (splits.stream().anyMatch(split -> !split.bookedOn().equals(bookedOn))) {
            throw new IllegalStateException("Splits must carry the transaction's booked_on");
        }
        this.categoryId = null;
        this.splits.clear();
        this.splits.addAll(splits);
    }

    public Split split(UUID categoryId, Money splitAmount, @Nullable String note) {
        return new Split(bookedOn, categoryId, splitAmount, note);
    }

    private UUID requireTransferAccount() {
        if (transferAccountId == null) {
            throw new IllegalStateException("Transfer without a receiving account");
        }
        return transferAccountId;
    }
}
