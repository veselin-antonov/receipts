package dev.vasoft.homeapp.receipts.purchases.api.response;

import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import java.time.LocalDate;

/**
 * A purchase as data, not presentation (SPEC §9.1). Amounts are always EUR,
 * converted from whatever currency the purchase was paid in, at full
 * precision; the UI rounds. The unit is in the field name so that no client
 * can mistake which currency a number is in (defect D11). The date is ISO
 * yyyy-MM-dd, like every date on the wire.
 *
 * <p>{@code priceEur} is the line total; the unit price is
 * {@code priceEur / quantity} per {@code quantityUnit}. {@code quantityAssumed}
 * marks a legacy row whose quantity was not recorded but set to one of the
 * product's unit by the migration. {@code quantity} and {@code quantityUnit}
 * are null only on a legacy row the migration has not reached yet.
 */
public record ResPurchase(
		String id,
		ResProduct product,
		double priceEur,
		LocalDate date,
		ResStore store,
		double discountAmountEur,
		Double quantity,
		QuantityUnit quantityUnit,
		boolean quantityAssumed
) {
}
