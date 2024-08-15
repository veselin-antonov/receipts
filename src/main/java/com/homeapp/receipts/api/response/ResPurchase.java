package com.homeapp.receipts.api.response;

public record ResPurchase(
		String id,
		ResProduct product,
		String price,
		String date,
		ResStore store,
		Boolean discount
) {
}
