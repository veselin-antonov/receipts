package dev.vasoft.homeapp.receipts.purchases.model.entities;

/**
 * The currency a purchase was paid in. Stored on every purchase and never
 * inferred from its date: the dataset spans Bulgaria's euro changeover, so it
 * is permanently mixed (SPEC §9.5, defect D11).
 */
public enum Currency {
    BGN,
    EUR;

    /**
     * The irrevocable conversion rate, 1 EUR = 1.95583 BGN. BGN was pegged at
     * this rate and adoption used the same one, so this is a constant, not a
     * market lookup.
     */
    public static final double BGN_PER_EUR = 1.95583;

    /**
     * Converts an amount in this currency to EUR at full precision. Rounding is
     * the UI's job; rounding here would accumulate error in anything computed
     * from the result.
     */
    public double toEur(double amount) {
        return switch (this) {
            case EUR -> amount;
            case BGN -> amount / BGN_PER_EUR;
        };
    }
}
