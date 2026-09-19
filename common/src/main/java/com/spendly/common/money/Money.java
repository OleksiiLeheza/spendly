package com.spendly.common.money;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

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
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money>, Serializable {

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

    /** Pseudo-currencies such as XAU report -1 fraction digits; treat those as whole units. */
    private static int scaleOf(Currency currency) {
        return Math.max(currency.getDefaultFractionDigits(), 0);
    }
}
