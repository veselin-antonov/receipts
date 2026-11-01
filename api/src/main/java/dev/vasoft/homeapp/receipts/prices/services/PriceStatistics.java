package dev.vasoft.homeapp.receipts.prices.services;

import dev.vasoft.homeapp.receipts.prices.api.response.ResPricePoint;
import dev.vasoft.homeapp.receipts.prices.api.response.ResPricePurchase;
import dev.vasoft.homeapp.receipts.prices.api.response.ResPriceRef;
import dev.vasoft.homeapp.receipts.prices.api.response.ResPriceStats;
import dev.vasoft.homeapp.receipts.prices.api.response.ResProductPrices;
import dev.vasoft.homeapp.receipts.prices.api.response.ResStorePrice;
import dev.vasoft.homeapp.receipts.prices.model.PurchasePriceRow;
import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import dev.vasoft.homeapp.receipts.stores.services.StoreMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 * Computes a product's price statistics from its purchases. Pure: no I/O, so
 * every rule here is unit-tested without a database.
 *
 * <p>Rules:
 * <ul>
 *   <li>Unit price = line total / quantity, in EUR (SPEC F2.0, §9.5). The
 *       discount is not subtracted: the price compared is the shelf price,
 *       which is also what the price check takes as input (F2.3).</li>
 *   <li>A purchase without a positive quantity and a known unit has no unit
 *       price. It is listed but left out of every figure, since guessing a
 *       quantity would give a confidently wrong verdict (F2.0a).</li>
 *   <li>Only one unit is compared. If a product was bought by the kilogram
 *       and by the piece, the figures use the unit with the most purchases,
 *       ties going to the unit of the most recent one.</li>
 * </ul>
 */
public final class PriceStatistics {

    private PriceStatistics() {
    }

    private record Priced(PurchasePriceRow row, PriceUnit unit, Double unitPriceEur) {
        boolean hasUnitPrice() {
            return unitPriceEur != null;
        }
    }

    /**
     * @param rows the product's purchases, newest first, as
     *             {@code PurchasesRepository.findPriceRows} returns them
     */
    public static ResProductPrices compute(ResProduct product, List<PurchasePriceRow> rows) {
        List<Priced> priced = rows.stream().map(PriceStatistics::price).toList();
        PriceUnit unit = comparisonUnit(priced);

        List<Priced> comparable = priced.stream()
            .filter(p -> p.hasUnitPrice() && p.unit() == unit)
            .toList();

        List<ResPricePurchase> purchases = priced.stream()
            .map(p -> toPurchase(p, p.hasUnitPrice() && p.unit() == unit))
            .toList();

        List<ResPricePoint> series = new ArrayList<>(comparable.size());
        for (int i = comparable.size() - 1; i >= 0; i--) {
            Priced p = comparable.get(i);
            Store store = p.row().store();
            series.add(new ResPricePoint(p.row().id().toHexString(), p.row().date(),
                p.unitPriceEur(), store == null ? null : store.getId().toHexString(),
                Boolean.TRUE.equals(p.row().quantityAssumed())));
        }

        return new ResProductPrices(product, unit == null ? null : unit.name(),
            stats(comparable), stores(comparable), series, purchases);
    }

    private static Priced price(PurchasePriceRow row) {
        Currency currency = row.currency();
        if (currency == null) {
            // As in PurchaseMapper: the startup backfill tags every legacy
            // row, so a null means it did not run. Guessing is how D11 happened.
            throw new IllegalStateException("Purchase " + row.id()
                + " has no currency; the legacy BGN backfill has not run");
        }
        PriceUnit.Normalized normalized = PriceUnit.normalize(row.quantity(), row.quantityUnit());
        if (normalized == null || row.price() == null) {
            return new Priced(row, null, null);
        }
        double unitPrice = currency.toEur(row.price()) / normalized.quantity();
        return new Priced(row, normalized.unit(), unitPrice);
    }

    private static PriceUnit comparisonUnit(List<Priced> priced) {
        Map<PriceUnit, Integer> counts = new EnumMap<>(PriceUnit.class);
        PriceUnit mostRecent = null;
        for (Priced p : priced) {
            if (!p.hasUnitPrice()) {
                continue;
            }
            if (mostRecent == null) {
                mostRecent = p.unit();
            }
            counts.merge(p.unit(), 1, Integer::sum);
        }
        if (mostRecent == null) {
            return null;
        }
        int best = counts.values().stream().max(Integer::compare).orElse(0);
        if (counts.get(mostRecent) == best) {
            return mostRecent;
        }
        return counts.entrySet().stream().filter(e -> e.getValue() == best)
            .map(Map.Entry::getKey).findFirst().orElse(mostRecent);
    }

    private static ResPriceStats stats(List<Priced> comparable) {
        if (comparable.isEmpty()) {
            return null;
        }
        int assumed = (int) comparable.stream()
            .filter(p -> Boolean.TRUE.equals(p.row().quantityAssumed())).count();
        return new ResPriceStats(comparable.size(), assumed, median(comparable),
            toRef(lowest(comparable)), toRef(comparable.getFirst()), usualStore(comparable));
    }

    /** Newest first in, so the first minimum found is the most recent one. */
    private static Priced lowest(List<Priced> comparable) {
        Priced lowest = comparable.getFirst();
        for (Priced p : comparable) {
            if (p.unitPriceEur() < lowest.unitPriceEur()) {
                lowest = p;
            }
        }
        return lowest;
    }

    static double median(List<Priced> comparable) {
        double[] prices = comparable.stream().mapToDouble(Priced::unitPriceEur).sorted().toArray();
        int mid = prices.length / 2;
        return prices.length % 2 == 1 ? prices[mid] : (prices[mid - 1] + prices[mid]) / 2;
    }

    private static ResStore usualStore(List<Priced> comparable) {
        Map<ObjectId, List<Priced>> byStore = byStore(comparable);
        // byStore keeps first-seen order, which is newest first, so on a tie
        // the first maximum is the store bought at most recently.
        List<Priced> usual = null;
        for (List<Priced> purchases : byStore.values()) {
            if (usual == null || purchases.size() > usual.size()) {
                usual = purchases;
            }
        }
        return usual == null ? null : StoreMapper.toResStore(usual.getFirst().row().store());
    }

    private static List<ResStorePrice> stores(List<Priced> comparable) {
        return byStore(comparable).values().stream()
            .map(purchases -> new ResStorePrice(
                StoreMapper.toResStore(purchases.getFirst().row().store()),
                purchases.size(),
                median(purchases),
                lowest(purchases).unitPriceEur(),
                toRef(purchases.getFirst())))
            .sorted(Comparator.comparingDouble(ResStorePrice::typicalUnitPriceEur)
                .thenComparing(s -> s.store().name(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /** Purchases with a store, grouped by it, in first-seen order. */
    private static Map<ObjectId, List<Priced>> byStore(List<Priced> comparable) {
        Map<ObjectId, List<Priced>> byStore = new LinkedHashMap<>();
        comparable.stream()
            .filter(p -> p.row().store() != null && p.row().store().getId() != null)
            .forEach(p -> byStore.computeIfAbsent(p.row().store().getId(),
                k -> new ArrayList<>()).add(p));
        return byStore;
    }

    private static ResPriceRef toRef(Priced p) {
        return new ResPriceRef(p.row().id().toHexString(), p.unitPriceEur(),
            StoreMapper.toResStore(p.row().store()), p.row().date());
    }

    private static ResPricePurchase toPurchase(Priced p, boolean comparable) {
        PurchasePriceRow row = p.row();
        Currency currency = row.currency();
        double price = row.price() == null ? 0.0 : row.price();
        double discount = row.discountAmount() == null ? 0.0 : row.discountAmount();
        return new ResPricePurchase(row.id().toHexString(), row.date(),
            StoreMapper.toResStore(row.store()),
            currency.toEur(price), currency.toEur(discount),
            row.quantity(), row.quantityUnit(),
            Boolean.TRUE.equals(row.quantityAssumed()),
            p.unitPriceEur(), p.unit() == null ? null : p.unit().name(),
            comparable);
    }
}
