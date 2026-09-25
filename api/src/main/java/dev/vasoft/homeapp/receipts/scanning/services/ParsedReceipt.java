package dev.vasoft.homeapp.receipts.scanning.services;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDate;
import java.util.List;

/**
 * Structured output schema for LLM receipt parsing.
 * Uses Jackson annotations for JSON schema generation and proper deserialization.
 *
 * <p>This record is part of the prompt: Spring AI turns it into the JSON schema
 * the model is told to follow. {@code required = true} is stated on every field
 * because Spring AI 2.x no longer marks fields required by default, and a
 * schema without {@code required} lets the model omit fields.
 * {@code ParsedReceiptSchemaTest} pins it.
 */
public record ParsedReceipt(
        @JsonProperty(value = "storeName", required = true)
        @JsonPropertyDescription("Name of the store or business where the purchase was made, as printed on the receipt header")
        String storeName,

        @JsonProperty(value = "receiptDate", required = true)
        @JsonPropertyDescription("Date of the purchase in dd/MM/yyyy format (e.g. 07/03/2026)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
        LocalDate receiptDate,

        @JsonProperty(value = "items", required = true)
        @JsonPropertyDescription("List of all purchased product items extracted from the receipt")
        List<ParsedItem> items
) {

    /**
     * Unit of measurement for product quantities.
     * Used when the receipt specifies a measurement unit alongside the quantity.
     */
    public enum QuantityUnit {
        @JsonProperty("PIECE")
        @JsonPropertyDescription("Item sold as individual pieces (default when no unit is specified)")
        PIECE,

        @JsonProperty("GRAM")
        @JsonPropertyDescription("Weight in grams (g)")
        GRAM,

        @JsonProperty("KILOGRAM")
        @JsonPropertyDescription("Weight in kilograms (kg)")
        KILOGRAM,

        @JsonProperty("MILLILITER")
        @JsonPropertyDescription("Volume in milliliters (ml)")
        MILLILITER,

        @JsonProperty("LITER")
        @JsonPropertyDescription("Volume in liters (l, L)")
        LITER
    }

    public record ParsedItem(
            @JsonProperty(value = "productName", required = true)
            @JsonPropertyDescription("Product name exactly as shown on the receipt line item")
            String productName,

            @JsonProperty(value = "price", required = true)
            @JsonPropertyDescription("The ORIGINAL listed price for this line item BEFORE any discounts or coupons are applied, as a decimal number (e.g. 12.99). This must be the full/gross price. If the receipt shows both an original and a reduced price, use the original (higher) price. If only one price is shown and a separate discount/coupon line reduces it, that single price IS the listed price.")
            double price,

            @JsonProperty(value = "quantity", required = true)
            @JsonPropertyDescription("Quantity purchased. Use the numeric value from the receipt (e.g. 2, 0.500, 1.5). Default to 1 if not specified.")
            double quantity,

            @JsonProperty(value = "quantityUnit", required = true)
            @JsonPropertyDescription("Unit of measurement for the quantity. Use PIECE if the item is sold by count or no unit is mentioned. Use GRAM, KILOGRAM, MILLILITER, or LITER when the receipt explicitly states a weight or volume unit.")
            QuantityUnit quantityUnit,

            @JsonProperty(value = "discountAmount", required = true)
            @JsonPropertyDescription("The total monetary amount of the discount applied to this item as a POSITIVE decimal number (e.g. 1.50). This is the absolute savings — the difference between the original listed price and the final price the customer actually paid. Compute this from whichever discount pattern is used on the receipt. Use 0.0 ONLY if no discount applies.")
            double discountAmount
    ) {
    }
}