package com.spendly.common.money;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An immutable monetary amount in a single currency.
 *
 * <p>
 * Backed by {@link BigDecimal} - never {@code double}. The amount is normalised to the currency's own scale on
 * construction, which is what makes the record's generated {@code equals} correct: {@code BigDecimal.equals} compares
 * scale as well as value, so {@code 10.00} and {@code 10.0} would otherwise be unequal.
 *
 * <p>
 * Arithmetic between different currencies is rejected rather than silently converted.
 *
 * <p>
 * Persistence: a JPA {@code @Embeddable}, so a service maps it onto the {@code <x>_value numeric(19,4)} +
 * {@code <x>_currency char(3)} column pair with two {@code @AttributeOverride}s (CLAUDE.md 6.4). Hibernate compares
 * {@code BigDecimal} attributes with {@code compareTo}, so the scale normalisation above never registers as a dirty
 * change.
 *
 * <p>
 * Wire format: {@code {"amount": "10.50", "currency": "USD"}}. The amount is a decimal <em>string</em> because a JSON
 * number is parsed through an IEEE-754 double by every JavaScript client before a decimal library can see it.
 */
@Embeddable
public record Money(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        @JdbcTypeCode(SqlTypes.CHAR) Currency currency)
        implements
            Comparable<Money>,
            Serializable {

    /**
     * Banker's rounding. Half-up biases every tie upward, which accumulates a systematic drift across a ledger;
     * half-even spreads ties in both directions.
     */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(scaleOf(currency), ROUNDING);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(long units, String currencyCode) {
        return new Money(BigDecimal.valueOf(units), Currency.getInstance(currencyCode));
    }

    /** Builds an amount from its smallest unit, e.g. {@code ofMinor(1099, "USD")} is USD 10.99. */
    public static Money ofMinor(long minorUnits, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        return new Money(BigDecimal.valueOf(minorUnits, scaleOf(currency)), currency);
    }

    public static Money zero(String currencyCode) {
        return zero(Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multipliedBy(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    public Money dividedBy(BigDecimal divisor) {
        return new Money(amount.divide(divisor, scaleOf(currency), ROUNDING), currency);
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    /** The amount expressed in the currency's smallest unit, e.g. USD 10.99 is {@code 1099}. */
    public long toMinorUnits() {
        return amount.movePointRight(scaleOf(currency)).longValueExact();
    }

    /**
     * Splits this amount into {@code parts} shares that sum back to exactly this amount.
     *
     * <p>
     * The indivisible remainder is spread one minor unit at a time over the leading shares, so splitting USD 10.00
     * three ways yields 3.34, 3.33, 3.33 - never 3.33 three times with a cent quietly lost.
     */
    public List<Money> allocate(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive, was " + parts);
        }
        long total = toMinorUnits();
        long base = total / parts;
        long remainder = Math.abs(total % parts);
        long step = total < 0 ? -1 : 1;

        List<Money> shares = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            long share = base + (i < remainder ? step : 0);
            shares.add(ofMinor(share, currency.getCurrencyCode()));
        }
        return List.copyOf(shares);
    }

    /**
     * Splits this amount proportionally to {@code weights}, so a share's size is {@code weight / sum(weights)} of the
     * whole. The largest-remainder method spreads the indivisible remainder over the shares whose ideal allocation was
     * cut the most, so the result still sums back to exactly this amount and no minor unit is lost.
     *
     * <p>
     * A weight of {@code 1} for every participant reproduces {@link #allocate(int)}; a percentage split passes
     * {@code List.of(60L, 40L)} and a shares split passes the raw share counts.
     */
    public List<Money> allocate(List<Long> weights) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        long weightSum = 0;
        for (long weight : weights) {
            if (weight < 0) {
                throw new IllegalArgumentException("a weight must not be negative");
            }
            weightSum += weight;
        }
        if (weightSum == 0) {
            throw new IllegalArgumentException("weights must sum to a positive number");
        }

        int size = weights.size();
        long total = toMinorUnits();
        long[] base = new long[size];
        long[] remainder = new long[size];
        long allocated = 0;
        for (int i = 0; i < size; i++) {
            long numerator = total * weights.get(i);
            base[i] = numerator / weightSum;
            remainder[i] = numerator - base[i] * weightSum;
            allocated += base[i];
        }

        long left = total - allocated;
        long step = left < 0 ? -1 : 1;
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingLong((Integer i) -> Math.abs(remainder[i])).reversed());
        for (int k = 0; k < Math.abs(left); k++) {
            base[order[k]] += step;
        }

        List<Money> shares = new ArrayList<>(size);
        for (long minorUnits : base) {
            shares.add(ofMinor(minorUnits, currency.getCurrencyCode()));
        }
        return List.copyOf(shares);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    /**
     * The number of decimal places this currency is booked at: 2 for USD, 0 for JPY, 3 for BHD. Pseudo-currencies such
     * as XAU report -1 fraction digits; treat those as whole units.
     */
    public static int scaleOf(Currency currency) {
        return Math.max(currency.getDefaultFractionDigits(), 0);
    }
}
