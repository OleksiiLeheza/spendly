package com.spendly.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        void scalesToTheCurrencyOnConstruction() {
            assertThat(Money.of("10.5", "USD").amount()).isEqualTo(new BigDecimal("10.50"));
        }

        @Test
        void treatsZeroDecimalCurrenciesAsWholeUnits() {
            assertThat(Money.of("1234.6", "JPY").amount()).isEqualTo(new BigDecimal("1235"));
        }

        @Test
        void equalityIgnoresTheScaleTheCallerTypedIn() {
            // BigDecimal.equals is scale-sensitive; normalising in the constructor is what
            // keeps the record's generated equals from surprising callers.
            assertThat(Money.of("10.0", "USD")).isEqualTo(Money.of("10.00", "USD"));
            assertThat(Money.of("10.0", "USD")).hasSameHashCodeAs(Money.of("10.000", "USD"));
        }

        @Test
        void differentCurrenciesAreNeverEqual() {
            assertThat(Money.of("10.00", "USD")).isNotEqualTo(Money.of("10.00", "EUR"));
        }
    }

    @Nested
    @DisplayName("scale rules")
    class ScaleRules {

        @ParameterizedTest(name = "{0} is booked at {1} decimal places")
        @CsvSource({"USD, 2", "EUR, 2", "JPY, 0", "KRW, 0", "BHD, 3", "KWD, 3", "XAU, 0"})
        void followsTheCurrencyFractionDigits(String code, int expectedScale) {
            Currency currency = Currency.getInstance(code);
            assertThat(Money.scaleOf(currency)).isEqualTo(expectedScale);
            assertThat(Money.zero(currency).amount().scale()).isEqualTo(expectedScale);
        }

        @Test
        void keepsThreeDecimalsForThreeDecimalCurrencies() {
            assertThat(Money.of("1.2345", "BHD").amount()).isEqualTo(new BigDecimal("1.234"));
            assertThat(Money.of("1.2355", "BHD").amount()).isEqualTo(new BigDecimal("1.236"));
        }

        @Test
        void minorUnitsFollowTheCurrencyScale() {
            assertThat(Money.ofMinor(1234, "BHD")).isEqualTo(Money.of("1.234", "BHD"));
            assertThat(Money.of("1.234", "BHD").toMinorUnits()).isEqualTo(1234L);
            assertThat(Money.of("1234", "JPY").toMinorUnits()).isEqualTo(1234L);
        }

        @Test
        void allocatesWholeUnitsForZeroDecimalCurrencies() {
            List<Money> shares = Money.of("100", "JPY").allocate(3);
            assertThat(shares).containsExactly(Money.of("34", "JPY"), Money.of("33", "JPY"), Money.of("33", "JPY"));
        }
    }

    @Nested
    @DisplayName("rounding policy")
    class Rounding {

        @ParameterizedTest(name = "{0} USD rounds to {1}")
        @CsvSource({
                "2.345, 2.34", // ties go to the even neighbour, not always up
                "2.355, 2.36",
                "2.344, 2.34",
                "2.346, 2.35",
                "-2.345, -2.34",
                "-2.355, -2.36"
        })
        void usesBankersRounding(String input, String expected) {
            assertThat(Money.of(input, "USD").amount()).isEqualTo(new BigDecimal(expected));
        }

        @Test
        void multiplicationRoundsHalfEvenToTheCurrencyScale() {
            assertThat(Money.of("10.00", "USD").multipliedBy(new BigDecimal("0.0125")))
                    .isEqualTo(Money.of("0.12", "USD"));
            assertThat(Money.of("10.00", "USD").multipliedBy(new BigDecimal("0.0175")))
                    .isEqualTo(Money.of("0.18", "USD"));
        }

        @Test
        void divisionRoundsHalfEvenInsteadOfThrowingOnRepeatingDecimals() {
            assertThat(Money.of("10.00", "USD").dividedBy(new BigDecimal("3"))).isEqualTo(Money.of("3.33", "USD"));
            assertThat(Money.of("0.05", "USD").dividedBy(new BigDecimal("2"))).isEqualTo(Money.of("0.02", "USD"));
            assertThat(Money.of("0.15", "USD").dividedBy(new BigDecimal("2"))).isEqualTo(Money.of("0.08", "USD"));
        }

        @Test
        void halfEvenDoesNotDriftUpwardAcrossManyTies() {
            // The reason HALF_EVEN is the default: HALF_UP would make this sum 0.15 too high.
            Money total = Money.zero("USD");
            for (int i = 0; i < 30; i++) {
                total = total.plus(Money.of("0.005", "USD"));
            }
            assertThat(total).isEqualTo(Money.zero("USD"));
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void addsAndSubtractsWithinOneCurrency() {
            assertThat(Money.of("10.00", "USD").plus(Money.of("2.50", "USD")))
                    .isEqualTo(Money.of("12.50", "USD"));
            assertThat(Money.of("10.00", "USD").minus(Money.of("12.50", "USD")))
                    .isEqualTo(Money.of("-2.50", "USD"));
        }

        @Test
        void rejectsMixingCurrencies() {
            assertThatThrownBy(() -> Money.of("10.00", "USD").plus(Money.of("1.00", "EUR")))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("USD")
                    .hasMessageContaining("EUR");
        }

        @Test
        void comparisonAlsoRejectsMixingCurrencies() {
            assertThatThrownBy(() -> Money.of("1.00", "USD").isGreaterThan(Money.of("1.00", "GBP")))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        void reportsSign() {
            assertThat(Money.of("0.00", "USD").isZero()).isTrue();
            assertThat(Money.of("0.01", "USD").isPositive()).isTrue();
            assertThat(Money.of("-0.01", "USD").isNegative()).isTrue();
            assertThat(Money.of("-5.00", "USD").abs()).isEqualTo(Money.of("5.00", "USD"));
            assertThat(Money.of("5.00", "USD").negated()).isEqualTo(Money.of("-5.00", "USD"));
        }
    }

    @Nested
    @DisplayName("minor units")
    class MinorUnits {

        @Test
        void roundTripsThroughTheSmallestUnit() {
            assertThat(Money.ofMinor(1099, "USD")).isEqualTo(Money.of("10.99", "USD"));
            assertThat(Money.of("10.99", "USD").toMinorUnits()).isEqualTo(1099L);
            assertThat(Money.ofMinor(1099, "JPY")).isEqualTo(Money.of("1099", "JPY"));
        }
    }

    @Nested
    @DisplayName("allocate")
    class Allocate {

        @Test
        void splitsWithoutLosingACent() {
            List<Money> shares = Money.of("10.00", "USD").allocate(3);
            assertThat(shares)
                    .containsExactly(Money.of("3.34", "USD"), Money.of("3.33", "USD"), Money.of("3.33", "USD"));
            assertThat(sum(shares)).isEqualTo(Money.of("10.00", "USD"));
        }

        @Test
        void splitsNegativeAmountsWithoutLosingACent() {
            List<Money> shares = Money.of("-10.00", "USD").allocate(3);
            assertThat(sum(shares)).isEqualTo(Money.of("-10.00", "USD"));
        }

        @Test
        void splitsEvenlyWhenItDivides() {
            assertThat(Money.of("9.99", "USD").allocate(3))
                    .containsOnly(Money.of("3.33", "USD"))
                    .hasSize(3);
        }

        @Test
        void rejectsNonPositivePartCounts() {
            assertThatThrownBy(() -> Money.of("1.00", "USD").allocate(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private static Money sum(List<Money> shares) {
            return shares.stream().reduce(Money.zero("USD"), Money::plus);
        }
    }

    @Nested
    @DisplayName("weighted allocate")
    class WeightedAllocate {

        @Test
        void splitsProportionallyWithoutLosingACent() {
            List<Money> shares = Money.of("100.00", "USD").allocate(List.of(60L, 40L));
            assertThat(shares).containsExactly(Money.of("60.00", "USD"), Money.of("40.00", "USD"));
        }

        @Test
        void spreadsTheRemainderOverTheMostCutShares() {
            List<Money> shares = Money.of("10.00", "USD").allocate(List.of(1L, 1L, 1L));
            assertThat(sum(shares)).isEqualTo(Money.of("10.00", "USD"));
            assertThat(shares)
                    .containsExactly(Money.of("3.34", "USD"), Money.of("3.33", "USD"), Money.of("3.33", "USD"));
        }

        @Test
        void equalWeightsMatchAllocateByPartCount() {
            assertThat(Money.of("10.00", "USD").allocate(List.of(1L, 1L, 1L)))
                    .isEqualTo(Money.of("10.00", "USD").allocate(3));
        }

        @Test
        void oneNonZeroWeightTakesEverything() {
            List<Money> shares = Money.of("10.00", "USD").allocate(List.of(0L, 5L, 0L));
            assertThat(shares).containsExactly(Money.zero("USD"), Money.of("10.00", "USD"), Money.zero("USD"));
        }

        @Test
        void rejectsAllZeroWeights() {
            assertThatThrownBy(() -> Money.of("10.00", "USD").allocate(List.of(0L, 0L)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsANegativeWeight() {
            assertThatThrownBy(() -> Money.of("10.00", "USD").allocate(List.of(1L, -1L)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsAnEmptyWeightList() {
            assertThatThrownBy(() -> Money.of("10.00", "USD").allocate(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void splitsNegativeAmountsWithoutLosingACent() {
            List<Money> shares = Money.of("-10.00", "USD").allocate(List.of(1L, 1L, 1L));
            assertThat(sum(shares)).isEqualTo(Money.of("-10.00", "USD"));
        }

        private static Money sum(List<Money> shares) {
            return shares.stream().reduce(Money.zero("USD"), Money::plus);
        }
    }

    @Test
    void rendersCurrencyFirst() {
        assertThat(Money.of("10.50", "USD")).hasToString("USD 10.50");
    }
}
