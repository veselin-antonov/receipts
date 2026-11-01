package dev.vasoft.homeapp.receipts.products.api.response;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import java.time.LocalDate;

/**
 * One product found by {@code GET /api/products/search}. Data, not presentation
 * (SPEC §9.1): the amount is EUR at full precision, the date ISO.
 *
 * @param relevance     how well the query names the product, 0 to 1; results are
 *                      ordered by this, nudged by how often and how recently the
 *                      user buys the product
 * @param purchaseCount how many times the user has bought it, 0 if never
 * @param lastPurchase  the user's most recent purchase of it, or null if none
 */
public record ResProductSearchResult(
		String id,
		String name,
		String iconID,
		double relevance,
		long purchaseCount,
		LastPurchase lastPurchase
) {

	/** What the user paid the last time, converted to EUR (SPEC §9.5). */
	public record LastPurchase(
			double priceEur,
			LocalDate date,
			ResStore store
	) {
	}
}
