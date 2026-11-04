package dev.vasoft.homeapp.receipts.purchases.model.entities;

/**
 * The unit a purchase's {@code quantity} is counted in. Stored as its name.
 *
 * <p>{@code PIECE} is not an absolute measure: a piece of yoghurt is whatever
 * the pot holds, so per-piece prices compare only within one package size
 * (SPEC F2.0a).
 */
public enum QuantityUnit {
    PIECE,
    GRAM,
    KILOGRAM,
    MILLILITER,
    LITER
}
