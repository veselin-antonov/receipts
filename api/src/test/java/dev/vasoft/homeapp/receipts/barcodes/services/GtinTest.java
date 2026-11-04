package dev.vasoft.homeapp.receipts.barcodes.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GtinTest {

    @Test
    void acceptsAnEan13AsItIs() {
        // 380 is GS1 Bulgaria's prefix
        assertThat(Gtin.normalize("3800178226137")).isEqualTo("3800178226137");
    }

    @Test
    void acceptsAnEan8AsItIs() {
        assertThat(Gtin.normalize("96385074")).isEqualTo("96385074");
    }

    @Test
    void storesUpcAAsTheEan13BehindIt() {
        // A scanner may report the same pack as UPC-A or as EAN-13
        assertThat(Gtin.normalize("036000291452")).isEqualTo("0036000291452");
        assertThat(Gtin.normalize("0036000291452")).isEqualTo("0036000291452");
    }

    @Test
    void storesAGtin14WithIndicatorZeroAsItsEan13() {
        assertThat(Gtin.normalize("03800178226137")).isEqualTo("3800178226137");
    }

    @Test
    void keepsAGtin14WithAnotherIndicator() {
        assertThat(Gtin.normalize("13800178226134")).isEqualTo("13800178226134");
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertThat(Gtin.normalize(" 3800178226137\n")).isEqualTo("3800178226137");
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertThatThrownBy(() -> Gtin.normalize("3800178226138"))
            .isInstanceOf(InvalidBarcodeException.class)
            .hasMessageContaining("check digit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "380017822613X", "3800-178226137", "-380017822613"})
    void rejectsAnythingButDigits(String input) {
        assertThatThrownBy(() -> Gtin.normalize(input))
            .isInstanceOf(InvalidBarcodeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "123456789", "12345678901", "123456789012345"})
    void rejectsOtherLengths(String input) {
        assertThatThrownBy(() -> Gtin.normalize(input))
            .isInstanceOf(InvalidBarcodeException.class)
            .hasMessageContaining("digits");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Gtin.normalize(null))
            .isInstanceOf(InvalidBarcodeException.class);
    }
}
