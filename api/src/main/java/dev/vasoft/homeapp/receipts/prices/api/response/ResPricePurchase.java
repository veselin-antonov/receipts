package dev.vasoft.homeapp.receipts.prices.api.response;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import java.time.LocalDate;

/**
 * One purchase in the history list (SPEC F2.4).
 *
 * @param priceEur          the line total, before the discount
 * @param quantity          as recorded, in {@code quantityUnit}; null if the
 *                          purchase has none
 * @param quantityUnit      as recorded: {@code PIECE}, {@code GRAM},
 *                          {@code KILOGRAM}, {@code MILLILITER} or {@code LITER}
 * @param quantityAssumed   true when the quantity was not read from a receipt
 *                          but set to 1 by the legacy migration
 * @param unitPriceEur      {@code priceEur} per {@code unit}; null without a
 *                          usable quantity
 * @param unit              what {@code unitPriceEur} is per, with grams and
 *                          millilitres converted to kilograms and litres
 * @param comparable        whether the statistics include this purchase: it
 *                          has a unit price, in the response's {@code unit}
 */
public record ResPricePurchase(
    String id,
    LocalDate date,
    ResStore store,
    double priceEur,
    double discountAmountEur,
    Double quantity,
    String quantityUnit,
    boolean quantityAssumed,
    Double unitPriceEur,
    String unit,
    boolean comparable
) {
}
