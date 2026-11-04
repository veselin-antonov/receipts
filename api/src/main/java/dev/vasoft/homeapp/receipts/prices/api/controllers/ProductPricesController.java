package dev.vasoft.homeapp.receipts.prices.api.controllers;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.prices.api.response.ResProductPrices;
import dev.vasoft.homeapp.receipts.prices.services.ProductPricesService;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Price history and statistics for one product (SPEC F2.2, F2.4). Kept apart
 * from {@code ProductController}, which owns the catalog.
 */
@RestController
@RequestMapping("/api/products/{productId}/prices")
public class ProductPricesController {

    private final ProductPricesService productPricesService;
    private final AuthenticatedUserService authenticatedUserService;

    public ProductPricesController(ProductPricesService productPricesService,
        AuthenticatedUserService authenticatedUserService) {
        this.productPricesService = productPricesService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping
    public ResponseEntity<ResProductPrices> getPrices(@AuthenticationPrincipal Jwt jwt,
        @PathVariable String productId) {
        ObjectId userId = authenticatedUserService.getUserId(jwt);
        if (!ObjectId.isValid(productId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.of(productPricesService.getPrices(userId, new ObjectId(productId)));
    }
}
