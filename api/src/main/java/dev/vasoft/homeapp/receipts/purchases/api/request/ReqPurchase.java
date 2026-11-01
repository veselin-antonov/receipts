package dev.vasoft.homeapp.receipts.purchases.api.request;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * A purchase to record, from the manual form or a reviewed scan.
 *
 * <p>{@code price} is the line total as paid, not the unit price: two pots at
 * 1.45 are {@code price: 2.90, quantity: 2} (SPEC F2.0). {@code quantity} and
 * {@code quantityUnit} are required, because a purchase without them has no
 * unit price (D1); a request without them is a 400, never a default.
 */
public record ReqPurchase(
		String productId,
		String productName,
		String storeId,
		String storeName,
		double price,
		/* The currency price and discountAmount are in; null means EUR. */
		Currency currency,
		LocalDate date,
		@NotNull(message = "quantity is required")
		@Positive(message = "quantity must be greater than 0")
		Double quantity,
		@NotNull(message = "quantityUnit is required")
		QuantityUnit quantityUnit,
		double discountAmount
) {
}
