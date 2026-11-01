package dev.vasoft.homeapp.receipts.barcodes.services;

/** The input is not a valid retail barcode. Answered with 400. */
public class InvalidBarcodeException extends RuntimeException {

    private final String barcode;

    public InvalidBarcodeException(String barcode, String reason) {
        super(reason);
        this.barcode = barcode;
    }

    public String getBarcode() {
        return barcode;
    }
}
