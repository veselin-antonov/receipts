package dev.vasoft.homeapp.receipts.barcodes.services;

import org.bson.types.ObjectId;

/**
 * The barcode belongs to another product. Answered with 409 and that product's
 * id, so the client can offer to open it or to move the barcode (unlink, then
 * link). A barcode is never moved silently.
 */
public class BarcodeAlreadyLinkedException extends RuntimeException {

    private final String barcode;
    private final ObjectId linkedProductId;

    public BarcodeAlreadyLinkedException(String barcode, ObjectId linkedProductId) {
        super("Barcode " + barcode + " is already linked to product "
            + (linkedProductId == null ? "(unknown)" : linkedProductId.toHexString()));
        this.barcode = barcode;
        this.linkedProductId = linkedProductId;
    }

    public String getBarcode() {
        return barcode;
    }

    public ObjectId getLinkedProductId() {
        return linkedProductId;
    }
}
