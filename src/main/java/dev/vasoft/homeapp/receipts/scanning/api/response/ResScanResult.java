package dev.vasoft.homeapp.receipts.scanning.api.response;

import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import java.time.LocalDate;
import java.util.List;

/**
 * Response containing all parsed purchases from a scanned receipt.
 * Returned for user review and editing before final submission.
 */
public record ResScanResult(
        ResScanStore storeSuggestion,
        String rawStoreName,
        LocalDate purchaseDate,
        List<ResScanPurchase> purchases
) {
}