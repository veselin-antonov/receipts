package dev.vasoft.homeapp.receipts.prices.services;

/**
 * The unit a unit price is quoted per. Purchases record grams and millilitres
 * as well; those are converted to kilograms and litres so that every price of
 * a product compares on one scale.
 *
 * <p>{@code PIECE} is one package, whatever its size: there is no package size
 * on products, so per-piece prices of one product compare directly.
 */
public enum PriceUnit {
    KILOGRAM,
    LITER,
    PIECE;

    /**
     * The quantity in this price unit, or null if the purchase cannot be
     * priced per unit: no unit, an unknown unit, or no positive quantity.
     */
    static Normalized normalize(Double quantity, String quantityUnit) {
        if (quantity == null || quantityUnit == null || !(quantity > 0)
            || quantity.isInfinite()) {
            return null;
        }
        return switch (quantityUnit) {
            case "KILOGRAM" -> new Normalized(KILOGRAM, quantity);
            case "GRAM" -> new Normalized(KILOGRAM, quantity / 1000);
            case "LITER" -> new Normalized(LITER, quantity);
            case "MILLILITER" -> new Normalized(LITER, quantity / 1000);
            case "PIECE" -> new Normalized(PIECE, quantity);
            default -> null;
        };
    }

    record Normalized(PriceUnit unit, double quantity) {
    }
}
