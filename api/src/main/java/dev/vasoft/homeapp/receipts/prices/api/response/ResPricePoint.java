package dev.vasoft.homeapp.receipts.prices.api.response;

import java.time.LocalDate;

/**
 * One dot on the history graph: a comparable purchase's unit price.
 *
 * @param storeId the store's id, matching an entry of {@code stores}; null if
 *                the purchase has no store
 */
public record ResPricePoint(
    String purchaseId,
    LocalDate date,
    double unitPriceEur,
    String storeId,
    boolean quantityAssumed
) {
}
