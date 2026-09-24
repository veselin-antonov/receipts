package dev.vasoft.homeapp.receipts.scanning.api.controllers;

import dev.vasoft.homeapp.receipts.scanning.services.ReceiptParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller advice for handling receipt scanning related exceptions.
 */
@RestControllerAdvice(basePackages = "dev.vasoft.homeapp.receipts.scanning")
public class ReceiptScanControllerAdvice {

    private final Logger logger = LoggerFactory.getLogger(ReceiptScanControllerAdvice.class);

    @ExceptionHandler(ReceiptParsingException.class)
    public ProblemDetail handleReceiptParsingException(ReceiptParsingException ex) {
        logger.error("Receipt parsing error: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage()
        );
        problemDetail.setTitle("Receipt Parsing Failed");
        problemDetail.setProperty("error", "RECEIPT_PARSING_ERROR");

        return problemDetail;
    }
}