package dev.vasoft.homeapp.receipts.purchases.api.request;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;

import java.time.LocalDate;

public record ReqPurchase(
		String productId,
    String productName,
		String storeId,
		String storeName,
		double price,
		/* The currency price and discountAmount are in; null means EUR. */
		Currency currency,
		LocalDate date,
		double quantity,
		QuantityUnit quantityUnit,
		double discountAmount
) {

	public enum QuantityUnit {
				PIECE,
				GRAM,
				KILOGRAM,
				MILLILITER,
				LITER;
		}}