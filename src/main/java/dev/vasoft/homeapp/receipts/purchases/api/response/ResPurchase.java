package dev.vasoft.homeapp.receipts.purchases.api.response;

import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;

public record ResPurchase(
		String id,
		ResProduct product,
		String price,
		String date,
		ResStore store,
		Double discountAmount
) {
}