package dev.vasoft.homeapp.receipts.scanning.services;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDate;
import java.util.List;

/**
 * Structured output schema for LLM receipt parsing.
 * Uses Jackson annotations for JSON schema generation and proper deserialization.
 */
public record ParsedReceipt(
        @JsonProperty("storeName")
        @JsonPropertyDescription("Name of the store where the purchase was made")
        String storeName,

        @JsonProperty("receiptDate")
        @JsonPropertyDescription("Date of the receipt in dd/MM/yyyy format")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
        LocalDate receiptDate,

        @JsonProperty("items")
        @JsonPropertyDescription("List of purchased items extracted from the receipt")
        List<ParsedItem> items
) {
    public record ParsedItem(
            @JsonProperty("productName")
            @JsonPropertyDescription("Product name as shown on the receipt")
            String productName,

            @JsonProperty("price")
            @JsonPropertyDescription("Price of the item in the local currency (as a decimal number)")
            double price,

            @JsonProperty("hasDiscount")
            @JsonPropertyDescription("Whether the item has a discount applied")
            boolean hasDiscount
    ) {
    }
}