package dev.vasoft.homeapp.receipts.prices.api.response;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;

/**
 * The verdict figures over a product's comparable purchases (SPEC F2.2).
 *
 * @param count               comparable purchases the figures are computed from
 * @param assumedCount        how many of those have an assumed quantity (legacy
 *                            rows the migration set to 1)
 * @param typicalUnitPriceEur the median unit price
 * @param lowest              the cheapest purchase; on a tie, the most recent
 * @param last                the most recent purchase
 * @param usualStore          the store bought at most often; on a tie, the one
 *                            bought at most recently. Null if no purchase has
 *                            a store
 */
public record ResPriceStats(
    int count,
    int assumedCount,
    double typicalUnitPriceEur,
    ResPriceRef lowest,
    ResPriceRef last,
    ResStore usualStore
) {
}
