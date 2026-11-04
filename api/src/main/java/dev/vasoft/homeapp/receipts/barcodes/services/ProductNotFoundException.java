package dev.vasoft.homeapp.receipts.barcodes.services;

/** The product id in the path is malformed or names no product. Answered with 404. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("No product with id " + productId);
    }
}
