package com.spendly.common.money;

import java.util.Currency;

/** Thrown when two {@link Money} amounts in different currencies are combined. */
public class CurrencyMismatchException extends IllegalArgumentException {

    private final transient Currency expected;
    private final transient Currency actual;

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Currency mismatch: expected %s but was %s".formatted(expected, actual));
        this.expected = expected;
        this.actual = actual;
    }

    public Currency expected() {
        return expected;
    }

    public Currency actual() {
        return actual;
    }
}
