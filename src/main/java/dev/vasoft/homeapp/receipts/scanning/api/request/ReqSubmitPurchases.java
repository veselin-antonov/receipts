package dev.vasoft.homeapp.receipts.scanning.api.request;

import dev.vasoft.homeapp.receipts.api.request.ReqPurchase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReqSubmitPurchases(
        @NotEmpty(message = "Purchases list cannot be empty")
        @Valid
        List<ReqPurchase> purchases
) {
}