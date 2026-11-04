package dev.vasoft.homeapp.receipts.prices.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.vasoft.homeapp.receipts.prices.api.response.ResPricePoint;
import dev.vasoft.homeapp.receipts.prices.api.response.ResPricePurchase;
import dev.vasoft.homeapp.receipts.prices.api.response.ResProductPrices;
import dev.vasoft.homeapp.receipts.prices.api.response.ResStorePrice;
import dev.vasoft.homeapp.receipts.prices.model.PurchasePriceRow;
import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class PriceStatisticsTest {

    private static final ResProduct PRODUCT = new ResProduct(new ObjectId().toHexString(),
        "Банани", null);

    private final Store lidl = store("Lidl");
    private final Store billa = store("Billa");
    private final Store kaufland = store("Kaufland");

    private static Store store(String name) {
        Store store = new Store(name);
        store.setId(new ObjectId());
        return store;
    }

    private static PurchasePriceRow row(String date, double price, Currency currency,
        Double quantity, String unit, Store store) {
        return new PurchasePriceRow(new ObjectId(), LocalDate.parse(date), price, currency, 0.0,
            quantity, unit, false, store);
    }

    private static PurchasePriceRow eur(String date, double price, double quantity, String unit,
        Store store) {
        return row(date, price, Currency.EUR, quantity, unit, store);
    }

    /** The repository returns rows newest first; tests list them in any order. */
    private static List<PurchasePriceRow> newestFirst(PurchasePriceRow... rows) {
        List<PurchasePriceRow> list = new ArrayList<>(List.of(rows));
        list.sort(Comparator.comparing(PurchasePriceRow::date).reversed());
        return list;
    }

    @Test
    void unitPriceIsTheLineTotalDividedByTheQuantity() {
        // SPEC F2.0: "1,240 x 2,49" comes back as price 3.09, quantity 1.24
        ResProductPrices prices = PriceStatistics.compute(PRODUCT,
            List.of(eur("2026-10-01", 3.0876, 1.24, "KILOGRAM", lidl)));

        assertThat(prices.unit()).isEqualTo("KILOGRAM");
        assertThat(prices.purchases().getFirst().unitPriceEur()).isCloseTo(2.49, within(1e-9));
        assertThat(prices.purchases().getFirst().priceEur()).isEqualTo(3.0876);
        assertThat(prices.stats().typicalUnitPriceEur()).isCloseTo(2.49, within(1e-9));
    }

    @Test
    void twoPiecesAtOnePriceCostThatPricePerPiece() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT,
            List.of(eur("2026-10-01", 2.90, 2, "PIECE", lidl)));

        assertThat(prices.unit()).isEqualTo("PIECE");
        assertThat(prices.stats().last().unitPriceEur()).isCloseTo(1.45, within(1e-9));
    }

    @Test
    void gramsAndMillilitresArePricedPerKilogramAndLitre() {
        ResProductPrices grams = PriceStatistics.compute(PRODUCT,
            List.of(eur("2026-10-01", 1.50, 500, "GRAM", lidl)));
        ResProductPrices millilitres = PriceStatistics.compute(PRODUCT,
            List.of(eur("2026-10-01", 1.20, 250, "MILLILITER", lidl)));

        assertThat(grams.unit()).isEqualTo("KILOGRAM");
        assertThat(grams.stats().last().unitPriceEur()).isCloseTo(3.00, within(1e-9));
        assertThat(grams.purchases().getFirst().quantityUnit()).isEqualTo("GRAM");
        assertThat(millilitres.unit()).isEqualTo("LITER");
        assertThat(millilitres.stats().last().unitPriceEur()).isCloseTo(4.80, within(1e-9));
    }

    @Test
    void bgnIsConvertedToEurBeforeDividing() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT,
            List.of(row("2025-03-01", 3.91166, Currency.BGN, 1.0, "KILOGRAM", lidl)));

        assertThat(prices.stats().last().unitPriceEur()).isCloseTo(2.00, within(1e-9));
        assertThat(prices.purchases().getFirst().priceEur()).isCloseTo(2.00, within(1e-9));
    }

    @Test
    void mixedCurrenciesCompareInEur() {
        // 1.95583 BGN is 1.00 EUR, cheaper than 1.10 EUR; compared raw it would be dearer
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            row("2025-06-01", 1.95583, Currency.BGN, 1.0, "PIECE", billa),
            eur("2026-06-01", 1.10, 1, "PIECE", lidl)));

        assertThat(prices.stats().lowest().store().name()).isEqualTo("Billa");
        assertThat(prices.stats().lowest().unitPriceEur()).isCloseTo(1.00, within(1e-9));
    }

    @Test
    void typicalPriceIsTheMedian() {
        ResProductPrices odd = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 1.00, 1, "PIECE", lidl),
            eur("2026-02-01", 5.00, 1, "PIECE", lidl),
            eur("2026-03-01", 2.00, 1, "PIECE", lidl)));
        ResProductPrices even = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 1.00, 1, "PIECE", lidl),
            eur("2026-02-01", 9.00, 1, "PIECE", lidl),
            eur("2026-03-01", 2.00, 1, "PIECE", lidl),
            eur("2026-04-01", 3.00, 1, "PIECE", lidl)));

        assertThat(odd.stats().typicalUnitPriceEur()).isEqualTo(2.00);
        assertThat(even.stats().typicalUnitPriceEur()).isEqualTo(2.50);
    }

    @Test
    void lowestAndLastCarryTheirStoreAndDate() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-10", 3.29, 1, "PIECE", lidl),
            eur("2026-02-10", 3.60, 1, "PIECE", billa),
            eur("2026-03-10", 3.49, 1, "PIECE", kaufland)));

        assertThat(prices.stats().lowest().store().name()).isEqualTo("Lidl");
        assertThat(prices.stats().lowest().date()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(prices.stats().last().store().name()).isEqualTo("Kaufland");
        assertThat(prices.stats().last().date()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void aTiedLowestPriceIsTheMostRecentOne() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-10", 2.00, 1, "PIECE", lidl),
            eur("2026-02-10", 2.00, 1, "PIECE", billa)));

        assertThat(prices.stats().lowest().store().name()).isEqualTo("Billa");
    }

    @Test
    void usualStoreIsTheMostFrequentWithTiesToTheMostRecent() {
        ResProductPrices frequent = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 1, 1, "PIECE", lidl),
            eur("2026-02-01", 1, 1, "PIECE", lidl),
            eur("2026-03-01", 1, 1, "PIECE", billa)));
        ResProductPrices tied = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 1, 1, "PIECE", lidl),
            eur("2026-02-01", 1, 1, "PIECE", billa)));

        assertThat(frequent.stats().usualStore().name()).isEqualTo("Lidl");
        assertThat(tied.stats().usualStore().name()).isEqualTo("Billa");
    }

    @Test
    void perStoreComparisonIsCheapestFirst() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 3.60, 1, "PIECE", billa),
            eur("2026-02-01", 3.29, 1, "PIECE", lidl),
            eur("2026-03-01", 3.49, 1, "PIECE", kaufland),
            eur("2026-04-01", 3.09, 1, "PIECE", lidl),
            eur("2026-05-01", 3.69, 1, "PIECE", lidl)));

        assertThat(prices.stores()).extracting(s -> s.store().name())
            .containsExactly("Lidl", "Kaufland", "Billa");
        ResStorePrice lidlLine = prices.stores().getFirst();
        assertThat(lidlLine.count()).isEqualTo(3);
        assertThat(lidlLine.typicalUnitPriceEur()).isEqualTo(3.29);
        assertThat(lidlLine.lowestUnitPriceEur()).isEqualTo(3.09);
        assertThat(lidlLine.last().date()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void purchasesAreNewestFirstAndTheSeriesOldestFirst() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 1, 1, "PIECE", lidl),
            eur("2026-03-01", 3, 1, "PIECE", billa),
            eur("2026-02-01", 2, 1, "PIECE", lidl)));

        assertThat(prices.purchases()).extracting(ResPricePurchase::date).containsExactly(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1));
        assertThat(prices.series()).extracting(ResPricePoint::date).containsExactly(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        assertThat(prices.series()).extracting(ResPricePoint::storeId).containsExactly(
            lidl.getId().toHexString(), lidl.getId().toHexString(), billa.getId().toHexString());
    }

    @Test
    void purchasesWithoutAQuantityAreListedButNotCounted() {
        // A legacy row the quantity migration has not reached yet
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            row("2025-01-01", 0.50, Currency.BGN, null, null, lidl),
            eur("2026-01-01", 2.00, 1, "PIECE", billa)));

        assertThat(prices.purchases()).hasSize(2);
        ResPricePurchase legacy = prices.purchases().getLast();
        assertThat(legacy.unitPriceEur()).isNull();
        assertThat(legacy.comparable()).isFalse();
        assertThat(prices.stats().count()).isEqualTo(1);
        assertThat(prices.stats().lowest().store().name()).isEqualTo("Billa");
        assertThat(prices.series()).hasSize(1);
    }

    @Test
    void aZeroQuantityHasNoUnitPrice() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT,
            List.of(eur("2026-01-01", 2.00, 0, "PIECE", lidl)));

        assertThat(prices.purchases().getFirst().unitPriceEur()).isNull();
        assertThat(prices.unit()).isNull();
        assertThat(prices.stats()).isNull();
        assertThat(prices.stores()).isEmpty();
        assertThat(prices.series()).isEmpty();
    }

    @Test
    void onlyTheMostFrequentUnitIsCompared() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 2.49, 1, "KILOGRAM", lidl),
            eur("2026-02-01", 2.59, 1, "KILOGRAM", lidl),
            eur("2026-03-01", 0.40, 1, "PIECE", lidl)));

        assertThat(prices.unit()).isEqualTo("KILOGRAM");
        assertThat(prices.stats().count()).isEqualTo(2);
        assertThat(prices.stats().last().date()).isEqualTo(LocalDate.of(2026, 2, 1));
        ResPricePurchase piece = prices.purchases().getFirst();
        assertThat(piece.unit()).isEqualTo("PIECE");
        assertThat(piece.unitPriceEur()).isEqualTo(0.40);
        assertThat(piece.comparable()).isFalse();
    }

    @Test
    void aTieBetweenUnitsGoesToTheMostRecentPurchasesUnit() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 2.49, 1, "KILOGRAM", lidl),
            eur("2026-02-01", 0.40, 1, "PIECE", lidl)));

        assertThat(prices.unit()).isEqualTo("PIECE");
    }

    @Test
    void assumedQuantitiesAreCountedAndFlagged() {
        PurchasePriceRow migrated = new PurchasePriceRow(new ObjectId(),
            LocalDate.of(2025, 1, 1), 1.95583, Currency.BGN, 0.0, 1.0, "PIECE", true, lidl);
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(migrated,
            eur("2026-01-01", 1.10, 1, "PIECE", lidl)));

        assertThat(prices.stats().assumedCount()).isEqualTo(1);
        assertThat(prices.purchases().getLast().quantityAssumed()).isTrue();
        assertThat(prices.series().getFirst().quantityAssumed()).isTrue();
    }

    @Test
    void theDiscountIsReportedButNotSubtracted() {
        PurchasePriceRow discounted = new PurchasePriceRow(new ObjectId(),
            LocalDate.of(2026, 1, 1), 4.00, Currency.EUR, 1.00, 2.0, "PIECE", false, lidl);
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, List.of(discounted));

        assertThat(prices.purchases().getFirst().discountAmountEur()).isEqualTo(1.00);
        assertThat(prices.stats().last().unitPriceEur()).isEqualTo(2.00);
    }

    @Test
    void purchasesWithoutAStoreCountButAreLeftOutOfTheStoreFigures() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, newestFirst(
            eur("2026-01-01", 1.00, 1, "PIECE", null),
            eur("2026-02-01", 2.00, 1, "PIECE", lidl)));

        assertThat(prices.stats().count()).isEqualTo(2);
        assertThat(prices.stats().lowest().store()).isNull();
        assertThat(prices.stats().usualStore().name()).isEqualTo("Lidl");
        assertThat(prices.stores()).singleElement()
            .satisfies(s -> assertThat(s.store().name()).isEqualTo("Lidl"));
        assertThat(prices.series().getFirst().storeId()).isNull();
    }

    @Test
    void aProductNeverBoughtHasAnEmptyHistory() {
        ResProductPrices prices = PriceStatistics.compute(PRODUCT, List.of());

        assertThat(prices.product()).isEqualTo(PRODUCT);
        assertThat(prices.unit()).isNull();
        assertThat(prices.stats()).isNull();
        assertThat(prices.stores()).isEmpty();
        assertThat(prices.series()).isEmpty();
        assertThat(prices.purchases()).isEmpty();
    }

    @Test
    void aPurchaseWithoutACurrencyFailsRatherThanGuessing() {
        List<PurchasePriceRow> rows = List.of(row("2025-01-01", 1.00, null, 1.0, "PIECE", lidl));

        assertThatThrownBy(() -> PriceStatistics.compute(PRODUCT, rows))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no currency");
    }
}
