package com.spendly.transactionservice.domain;

import com.spendly.common.money.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Embeddable
public record Split(
        @Column(name = "booked_on", nullable = false) LocalDate bookedOn,
        @Column(name = "category_id", nullable = false) UUID categoryId,
        @Embedded @AttributeOverride(name = "amount", column = @Column(name = "amount_value", nullable = false, precision = 19, scale = 4)) @AttributeOverride(name = "currency", column = @Column(name = "amount_currency", nullable = false, columnDefinition = "char(3)")) Money amount,
        @Column(name = "note", length = 200) @Nullable String note) {
}
