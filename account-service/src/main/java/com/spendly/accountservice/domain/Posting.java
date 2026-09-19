package com.spendly.accountservice.domain;

import com.spendly.common.money.Money;
import com.spendly.common.persistence.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(name = "postings")
@EntityListeners(AuditingEntityListener.class)
public class Posting extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "ledger_entry_id", nullable = false, updatable = false)
    private UUID ledgerEntryId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount_value", nullable = false, updatable = false, precision = 19, scale = 4))
    @AttributeOverride(name = "currency", column = @Column(name = "amount_currency", nullable = false, updatable = false, columnDefinition = "char(3)"))
    private Money amount;

    @Column(name = "booked_on", nullable = false, updatable = false)
    private LocalDate bookedOn;

    @CreatedBy
    @Column(name = "recorded_by", updatable = false)
    private @Nullable UUID recordedBy;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected Posting() {
    }

    Posting(Account account, UUID ledgerEntryId, Money amount, LocalDate bookedOn) {
        this.account = account;
        this.ownerId = account.getOwnerId();
        this.ledgerEntryId = ledgerEntryId;
        this.amount = amount;
        this.bookedOn = bookedOn;
    }
}
