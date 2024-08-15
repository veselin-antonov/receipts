package com.homeapp.receipts.api.response;

import java.util.List;

public record ResProductDetails(
		ResStatistics stats,
		List<ResPurchase> purchases
) {
}
