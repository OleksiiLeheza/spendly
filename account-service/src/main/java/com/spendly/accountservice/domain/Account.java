package com.spendly.accountservice.domain;

import com.spendly.common.money.CurrencyMismatchException;
import com.spendly.common.money.Money;
import com.spendly.common.persistence.BaseEntityAudit;
import com.spendly.common.web.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Getter
@Setter
@Table(name = "accounts")
public class Account extends BaseEntityAudit {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AccountType type;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "balance_value", nullable = false, precision = 19, scale = 4))
    @AttributeOverride(name = "currency", column = @Column(name = "balance_currency", nullable = false, columnDefinition = "char(3)"))
    private Money balance;

    @Column(name = "archived_at")
    private @Nullable Instant archivedAt;

    public static Account open(UUID ownerId, String name, AccountType type, Currency currency) {
        Account account = new Account();
        account.ownerId = ownerId;
        account.name = name;
        account.type = type;
        account.balance = Money.zero(currency);
        return account;
    }

    public Currency currency() {
        return balance.currency();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void archive(Instant at) {
        if (archivedAt == null) {
            archivedAt = at;
        }
    }

    public Posting post(UUID ledgerEntryId, Money amount, LocalDate bookedOn) {
        if (isArchived()) {
            throw new ApiException.Unprocessable("Account " + getId() + " is archived and accepts no postings");
        }
        try {
            balance = balance.plus(amount);
        } catch (CurrencyMismatchException ex) {
            throw new ApiException.Unprocessable("Account " + getId() + " is held in " + currency()
                    + " and cannot take a posting in " + amount.currency());
        }
        return new Posting(this, ledgerEntryId, amount, bookedOn);
    }

    public void replaceBalance(Money recalculated) {
        if (!recalculated.currency().equals(currency())) {
            throw new CurrencyMismatchException(currency(), recalculated.currency());
        }
        balance = recalculated;
    }
}
