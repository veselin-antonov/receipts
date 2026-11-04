package dev.vasoft.homeapp.receipts.prices.model;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import java.time.LocalDate;
import org.bson.types.ObjectId;

/**
 * One purchase of a product, as the price history reads it: only the fields a
 * price needs, with the store already joined. A read-only projection of the
 * {@code purchases} collection, so the price history does not depend on the
 * {@code Purchase} entity's shape.
 *
 * <p>{@code price} is the line total in {@code currency}, before the discount
 * (SPEC F2.0). {@code quantity} and {@code quantityUnit} are null on legacy
 * rows the quantity migration has not reached yet; {@code quantityAssumed} is
 * true on rows that migration wrote.
 */
public record PurchasePriceRow(
    ObjectId id,
    LocalDate date,
    Double price,
    Currency currency,
    Double discountAmount,
    Double quantity,
    String quantityUnit,
    Boolean quantityAssumed,
    Store store
) {
}
