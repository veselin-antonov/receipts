package dev.vasoft.homeapp.receipts.scanning.services;

/**
 * Exception thrown when receipt parsing fails.
 */
public class ReceiptParsingException extends RuntimeException {

    public ReceiptParsingException(String message) {
        super(message);
    }

    public ReceiptParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}