package dev.vasoft.homeapp.receipts.prices.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.prices.api.response.ResProductPrices;
import dev.vasoft.homeapp.receipts.prices.services.ProductPricesService;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ProductPricesControllerTest {

    @Mock
    private ProductPricesService productPricesService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private Jwt jwt;

    private ProductPricesController controller;

    private final ObjectId userId = new ObjectId();

    @BeforeEach
    void setUp() {
        controller = new ProductPricesController(productPricesService, authenticatedUserService);
        when(authenticatedUserService.getUserId(jwt)).thenReturn(userId);
    }

    @Test
    void returnsTheAuthenticatedUsersPrices() {
        ObjectId productId = new ObjectId();
        ResProductPrices prices = new ResProductPrices(null, null, null, List.of(), List.of(),
            List.of());
        when(productPricesService.getPrices(userId, productId)).thenReturn(Optional.of(prices));

        ResponseEntity<ResProductPrices> response =
            controller.getPrices(jwt, productId.toHexString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(prices);
    }

    @Test
    void anUnknownProductIsNotFound() {
        ObjectId productId = new ObjectId();
        when(productPricesService.getPrices(userId, productId)).thenReturn(Optional.empty());

        assertThat(controller.getPrices(jwt, productId.toHexString()).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aMalformedIdIsNotFoundAndNeverQueried() {
        assertThat(controller.getPrices(jwt, "not-an-id").getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        verify(productPricesService, never()).getPrices(any(), any());
    }
}
