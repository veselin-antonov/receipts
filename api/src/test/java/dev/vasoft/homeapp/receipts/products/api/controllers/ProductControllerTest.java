package dev.vasoft.homeapp.receipts.products.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.products.api.response.ResProductSearchResult;
import dev.vasoft.homeapp.receipts.products.services.ProductService;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private Jwt jwt;

    @Test
    void searchIsScopedToTheAuthenticatedUser() {
        ObjectId userId = new ObjectId();
        List<ResProductSearchResult> expected = List.of(
            new ResProductSearchResult("id", "Мляко", null, 1.0, 0, null));
        when(authenticatedUserService.getUserId(jwt)).thenReturn(userId);
        when(productService.search(userId, "мляко", 5)).thenReturn(expected);

        ProductController controller = new ProductController(productService,
            authenticatedUserService);

        assertThat(controller.search(jwt, "мляко", 5)).isSameAs(expected);
    }
}
