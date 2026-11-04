package dev.vasoft.homeapp.receipts.barcodes.services;

/**
 * No product carries this barcode yet. Answered with 404; for the scanner this
 * is the normal "first scan" case, which asks the user which product it is.
 */
public class BarcodeNotLinkedException extends RuntimeException {

    private final String barcode;

    public BarcodeNotLinkedException(String barcode) {
        super("No product is linked to barcode " + barcode);
        this.barcode = barcode;
    }

    public String getBarcode() {
        return barcode;
    }
}
