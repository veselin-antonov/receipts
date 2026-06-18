package dev.vasoft.homeapp.receipts.scanning.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.receipts.common.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.StoresRepository;
import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanPurchase;
import dev.vasoft.homeapp.receipts.scanning.services.ParsedReceipt.ParsedItem;
import dev.vasoft.homeapp.receipts.scanning.services.ParsedReceipt.QuantityUnit;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatcherServiceTest {

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private ProductRepository productRepository;

    private MatcherService matcherService;

    @BeforeEach
    void setUp() {
        matcherService = new MatcherService(storesRepository, productRepository);
    }

    @Test
    void matchProductsToPurchasesRanksExactCanonicalNameFirst() {
        Product exactProduct = product("Coca Cola Zero", List.of());
        Product otherProduct = product("Mineral Water", List.of());
        when(productRepository.findAll()).thenReturn(List.of(otherProduct, exactProduct));

        List<ResScanPurchase> purchases = matcherService.matchProductsToPurchases(List.of(
            parsedItem("Coca Cola Zero")
        ));

        assertThat(purchases).hasSize(1);
        assertThat(purchases.getFirst().rawProductName()).isEqualTo("Coca Cola Zero");
        assertThat(purchases.getFirst().productSuggestions())
            .extracting(ResProduct::name)
            .startsWith("Coca Cola Zero");
    }

    @Test
    void matchProductsToPurchasesRanksExactAliasMatchFirst() {
        Product aliasProduct = product("Coca Cola Zero", List.of("Cola No Sugar"));
        Product otherProduct = product("Chocolate", List.of());
        when(productRepository.findAll()).thenReturn(List.of(otherProduct, aliasProduct));

        List<ResScanPurchase> purchases = matcherService.matchProductsToPurchases(List.of(
            parsedItem("Cola No Sugar")
        ));

        assertThat(purchases).hasSize(1);
        assertThat(purchases.getFirst().productSuggestions())
            .extracting(ResProduct::name)
            .startsWith("Coca Cola Zero");
    }

    @Test
    void matchProductsToPurchasesIncludesFuzzyCandidatesForMisspelledReceiptNames() {
        Product bestMatch = product("Coca Cola Zero", List.of());
        Product weakerMatch = product("Cocoa Biscuits", List.of());
        Product unrelated = product("Laundry Detergent", List.of());
        when(productRepository.findAll()).thenReturn(List.of(unrelated, weakerMatch, bestMatch));

        List<ResScanPurchase> purchases = matcherService.matchProductsToPurchases(List.of(
            parsedItem("Coc Cola Zro")
        ));

        assertThat(purchases).hasSize(1);
        assertThat(purchases.getFirst().productSuggestions())
            .extracting(ResProduct::name)
            .contains("Coca Cola Zero")
            .doesNotContain("Laundry Detergent");
        assertThat(purchases.getFirst().productSuggestions().getFirst().name())
            .isEqualTo("Coca Cola Zero");
    }

    private static ParsedItem parsedItem(String productName) {
        return new ParsedItem(productName, 2.50, 1.0, QuantityUnit.PIECE, 0.0);
    }

    private static Product product(String canonicalName, List<String> aliases) {
        Product product = new Product();
        product.setId(new ObjectId());
        product.setCanonicalName(canonicalName);
        product.setNormalizedCanonicalName(NormalizationService.normalizeProductName(canonicalName));
        product.setAliases(aliases);
        product.setNormalizedAliases(aliases.stream()
            .map(NormalizationService::normalizeProductName)
            .toList());
        product.setIconID("test-icon");
        return product;
    }
}
