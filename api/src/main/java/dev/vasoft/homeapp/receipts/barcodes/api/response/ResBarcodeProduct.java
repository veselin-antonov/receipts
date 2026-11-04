package dev.vasoft.homeapp.receipts.barcodes.api.response;

import java.util.List;

/**
 * A product as the barcode endpoints return it: {@code ResProduct}'s fields
 * plus the product's barcodes, always a list (empty when it has none).
 */
public record ResBarcodeProduct(
    String id,
    String name,
    String iconID,
    List<String> barcodes
) {
}
