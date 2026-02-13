package dev.vasoft.homeapp.receipts.scanning.api.response;

import java.util.List;

/**
 * Response containing all parsed purchases from a scanned receipt.
 * Returned for user review and editing before final submission.
 */
public record ResScanResult(
        String storeName,
        String receiptDate,
        List<ResParsedPurchase> purchases,
        String rawText
) {
}