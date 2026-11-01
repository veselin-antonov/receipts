package dev.vasoft.homeapp.receipts.prices.api.response;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;

/**
 * One store's line in the per-store comparison, over the product's comparable
 * purchases at that store.
 *
 * @param typicalUnitPriceEur the median unit price at this store
 */
public record ResStorePrice(
    ResStore store,
    int count,
    double typicalUnitPriceEur,
    double lowestUnitPriceEur,
    ResPriceRef last
) {
}
