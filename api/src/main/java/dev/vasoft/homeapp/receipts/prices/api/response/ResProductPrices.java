package dev.vasoft.homeapp.receipts.prices.api.response;

import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import java.util.List;

/**
 * A product's price history and statistics for the authenticated user
 * ({@code GET /api/products/{id}/prices}). Every price is a unit price in EUR,
 * at full precision: the line total divided by the quantity, converted from
 * the currency paid (SPEC F2.0, §9.5). The UI rounds.
 *
 * @param unit      what the unit prices are per: {@code KILOGRAM},
 *                  {@code LITER} or {@code PIECE}. When a product was bought
 *                  in more than one unit, the most frequent one; null when no
 *                  purchase has a usable quantity
 * @param stats     the verdict figures, or null when no purchase is comparable
 * @param stores    one entry per store, cheapest typical price first
 * @param series    the comparable purchases oldest first, for the graph
 * @param purchases every purchase of the product, newest first, including
 *                  those the statistics leave out
 */
public record ResProductPrices(
    ResProduct product,
    String unit,
    ResPriceStats stats,
    List<ResStorePrice> stores,
    List<ResPricePoint> series,
    List<ResPricePurchase> purchases
) {
}
