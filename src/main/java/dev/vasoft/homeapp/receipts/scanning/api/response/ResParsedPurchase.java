package dev.vasoft.homeapp.receipts.scanning.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.vasoft.homeapp.receipts.scanning.services.ParsedReceipt;

import java.time.LocalDate;

/**
 * Represents a single parsed purchase item from a receipt.
 * This is returned by the LLM parsing service for user review before submission.
 */
public record ResParsedPurchase(
        String product,
        String store,
        double price,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
        LocalDate date,
        double quantity,
        String quantityUnit,
        double discountAmount
) {
}