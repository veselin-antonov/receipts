package dev.vasoft.homeapp.receipts.products.api.response;

import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.common.api.response.ResStatistics;
import java.util.List;

public record ResProductDetails(
		ResStatistics stats,
		List<ResPurchase> purchases
) {
}