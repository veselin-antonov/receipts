package dev.vasoft.homeapp.receipts.purchases.model.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class CurrencyTest {

    @Test
    void theRateIsTheIrrevocableOne() {
        assertThat(Currency.BGN_PER_EUR).isEqualTo(1.95583);
    }

    @Test
    void eurIsUnchanged() {
        assertThat(Currency.EUR.toEur(3.29)).isEqualTo(3.29);
    }

    @Test
    void bgnConvertsAtFullPrecision() {
        // SPEC §9.5: 12.65 BGN is 6.4678... EUR, and stays unrounded here.
        assertThat(Currency.BGN.toEur(12.65)).isCloseTo(6.467842297, within(1e-9));
        assertThat(Currency.BGN.toEur(12.65)).isNotEqualTo(6.47);
    }

    @Test
    void oneEurWorthOfBgnIsOneEur() {
        assertThat(Currency.BGN.toEur(1.95583)).isEqualTo(1.0);
    }
}
