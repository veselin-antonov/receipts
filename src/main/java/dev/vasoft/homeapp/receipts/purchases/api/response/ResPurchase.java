package dev.vasoft.homeapp.receipts.purchases.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import java.time.LocalDate;

/**
 * A purchase as data, not presentation (SPEC §9.1). Amounts are always EUR,
 * converted from whatever currency the purchase was paid in, at full
 * precision; the UI rounds. The unit is in the field name so that no client can
 * mistake which currency a number is in (defect D11).
 */
public record ResPurchase(
		String id,
		ResProduct product,
		double priceEur,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate date,
		ResStore store,
		double discountAmountEur
) {
}
