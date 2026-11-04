package dev.vasoft.homeapp.receipts.prices.api.response;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import java.time.LocalDate;

/** One purchase's unit price, with where and when it was paid. */
public record ResPriceRef(
    String purchaseId,
    double unitPriceEur,
    ResStore store,
    LocalDate date
) {
}
