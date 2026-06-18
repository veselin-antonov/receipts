package dev.vasoft.homeapp.receipts.scanning.api.response;

import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import java.util.List;

public record ResScanPurchase(String rawProductName, List<ResProduct> productSuggestions,
                              Double price, Double quantity, String quantityUnit,
                              Double discountAmount) {

}