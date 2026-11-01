package dev.vasoft.homeapp.receipts.barcodes.api.controllers;

import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeAlreadyLinkedException;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeNotLinkedException;
import dev.vasoft.homeapp.receipts.barcodes.services.InvalidBarcodeException;
import dev.vasoft.homeapp.receipts.barcodes.services.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every barcode failure is a {@code ProblemDetail} with a machine-readable
 * {@code error}, so the ui branches on that and never on the status alone
 * (the same rule as {@code ACCOUNT_NOT_VERIFIED}, SPEC D15).
 */
@RestControllerAdvice(assignableTypes = BarcodeController.class)
public class BarcodeControllerAdvice {

    @ExceptionHandler(InvalidBarcodeException.class)
    public ProblemDetail invalid(InvalidBarcodeException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid Barcode",
            e.getMessage(), "INVALID_BARCODE");
        problem.setProperty("barcode", e.getBarcode());
        return problem;
    }

    @ExceptionHandler(BarcodeNotLinkedException.class)
    public ProblemDetail notLinked(BarcodeNotLinkedException e) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Barcode Not Linked",
            e.getMessage(), "BARCODE_NOT_LINKED");
        problem.setProperty("barcode", e.getBarcode());
        return problem;
    }

    @ExceptionHandler(BarcodeAlreadyLinkedException.class)
    public ProblemDetail alreadyLinked(BarcodeAlreadyLinkedException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Barcode Already Linked",
            e.getMessage(), "BARCODE_ALREADY_LINKED");
        problem.setProperty("barcode", e.getBarcode());
        problem.setProperty("productId", e.getLinkedProductId() == null ? null
            : e.getLinkedProductId().toHexString());
        return problem;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail productNotFound(ProductNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Product Not Found", e.getMessage(),
            "PRODUCT_NOT_FOUND");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail,
                                         String error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("error", error);
        return problem;
    }
}
