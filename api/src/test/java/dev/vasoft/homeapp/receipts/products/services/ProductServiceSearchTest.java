package dev.vasoft.homeapp.receipts.products.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.receipts.common.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.products.api.response.ResProductSearchResult;
import dev.vasoft.homeapp.receipts.products.model.ProductPurchaseSummary;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceSearchTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Mock
    private ProductRepository productRepository;

    private ProductService service;
    private final ObjectId userId = new ObjectId();
    private final List<ProductPurchaseSummary> summaries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        service = new ProductService(productRepository, clock);
        lenient().when(productRepository.getPurchaseSummaries(userId)).thenReturn(summaries);
    }

    private static Product product(String name, String... aliases) {
        Product p = new Product(name);
        p.setId(new ObjectId());
        p.setAliases(List.of(aliases));
        return p;
    }

    private void bought(Product product, long count, LocalDate last, double price,
                        Currency currency, Store store) {
        summaries.add(new ProductPurchaseSummary(product.getId(), count, last, price, currency,
            store));
    }

    private List<String> names(List<ResProductSearchResult> results) {
        return results.stream().map(ResProductSearchResult::name).toList();
    }

    @Test
    void findsProductsRegardlessOfWordOrder() {
        Product milk = product("Прясно мляко");
        Product bread = product("Хляб Добруджа");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(milk, bread));

        assertThat(names(service.search(userId, "мляко прясно", 0)))
            .containsExactly("Прясно мляко");
    }

    @Test
    void matchesOnAliases() {
        Product cola = product("Coca-Cola", "Кока Кола 2л");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(cola));

        List<ResProductSearchResult> results = service.search(userId, "кока кола", 0);

        assertThat(names(results)).containsExactly("Coca-Cola");
        assertThat(results.getFirst().relevance()).isGreaterThan(0.9);
    }

    @Test
    void aBetterMatchBeatsAMoreBoughtProduct() {
        Product exact = product("Кисело мляко Верея");
        Product partial = product("Кисело мляко Данон");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(partial, exact));
        // Bought far more often, and today, but misses a word of the query
        bought(partial, 50, TODAY, 1.20, Currency.EUR, null);

        assertThat(names(service.search(userId, "кисело мляко верея", 0)))
            .containsExactly("Кисело мляко Верея", "Кисело мляко Данон");
    }

    @Test
    void habitOutweighsExtraWordsInTheName() {
        // Both contain every word of the query; the one the user actually buys
        // comes first even though its name is longer
        Product plain = product("Кисело мляко");
        Product usual = product("Кисело мляко Верея 2% 400г");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(plain, usual));
        bought(usual, 50, TODAY, 1.20, Currency.EUR, null);

        assertThat(names(service.search(userId, "кисело мляко", 0)))
            .containsExactly("Кисело мляко Верея 2% 400г", "Кисело мляко");
    }

    @Test
    void equallyRelevantProductsAreOrderedByHowOftenAndRecentlyTheyAreBought() {
        Product a = product("Мляко Верея");
        Product b = product("Мляко Олимпус");
        Product c = product("Мляко Данон");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(a, b, c));
        bought(a, 2, TODAY.minusYears(2), 1.0, Currency.EUR, null);
        bought(b, 30, TODAY.minusDays(3), 1.0, Currency.EUR, null);

        assertThat(names(service.search(userId, "мляко", 0)))
            .containsExactly("Мляко Олимпус", "Мляко Верея", "Мляко Данон");
    }

    @Test
    void theNameBreaksRemainingTies() {
        Product b = product("Банани");
        Product a = product("Банани");
        a.setCanonicalName("банани");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(b, a));

        assertThat(names(service.search(userId, "банани", 0))).containsExactly("Банани", "банани");
    }

    @Test
    void carriesTheLastPaidPriceInEur() {
        Product cheese = product("Сирене");
        Store lidl = new Store(new ObjectId(), "Lidl", "lidl", "lidl");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(cheese));
        bought(cheese, 4, LocalDate.of(2025, 9, 1), 12.65, Currency.BGN, lidl);

        ResProductSearchResult result = service.search(userId, "сирене", 0).getFirst();

        assertThat(result.purchaseCount()).isEqualTo(4);
        assertThat(result.lastPurchase()).isNotNull();
        assertThat(result.lastPurchase().priceEur()).isCloseTo(12.65 / 1.95583, within(1e-12));
        assertThat(result.lastPurchase().date()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(result.lastPurchase().store().name()).isEqualTo("Lidl");
        assertThat(result.id()).isEqualTo(cheese.getId().toHexString());
    }

    @Test
    void aProductNeverBoughtHasNoLastPurchase() {
        when(productRepository.getProductsForSearch()).thenReturn(List.of(product("Сирене")));

        ResProductSearchResult result = service.search(userId, "сирене", 0).getFirst();

        assertThat(result.purchaseCount()).isZero();
        assertThat(result.lastPurchase()).isNull();
    }

    @Test
    void aLastPurchaseWithoutCurrencyIsLeftOutRatherThanGuessed() {
        Product cheese = product("Сирене");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(cheese));
        summaries.add(new ProductPurchaseSummary(cheese.getId(), 1, TODAY, 5.0, null, null));

        assertThat(service.search(userId, "сирене", 0).getFirst().lastPurchase()).isNull();
    }

    @Test
    void poorMatchesAreDropped() {
        when(productRepository.getProductsForSearch())
            .thenReturn(List.of(product("Карфиол на брой"), product("Кашкавал")));

        assertThat(service.search(userId, "кафе на зърна", 0)).isEmpty();
    }

    @Test
    void blankQueriesReturnNothingWithoutReadingTheDatabase() {
        assertThat(service.search(userId, "", 0)).isEmpty();
        assertThat(service.search(userId, "   ", 0)).isEmpty();
        assertThat(service.search(userId, null, 0)).isEmpty();
        assertThat(service.search(userId, "-- !", 0)).isEmpty();
        verify(productRepository, never()).getProductsForSearch();
    }

    @Test
    void doesNotReadPurchasesWhenNothingMatches() {
        when(productRepository.getProductsForSearch()).thenReturn(List.of(product("Кашкавал")));

        assertThat(service.search(userId, "хляб", 0)).isEmpty();
        verify(productRepository, never()).getPurchaseSummaries(any());
    }

    @Test
    void limitsTheNumberOfResults() {
        List<Product> many = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            many.add(product("Мляко " + i));
        }
        when(productRepository.getProductsForSearch()).thenReturn(many);

        assertThat(service.search(userId, "мляко", 0)).hasSize(ProductService.DEFAULT_LIMIT);
        assertThat(service.search(userId, "мляко", 5)).hasSize(5);
        assertThat(service.search(userId, "мляко", 1000)).hasSize(ProductService.MAX_LIMIT);
    }

    @Test
    void usageBlendsFrequencyAndRecency() {
        ObjectId id = new ObjectId();
        assertThat(ProductService.usage(null, 10, TODAY)).isZero();
        assertThat(ProductService.usage(new ProductPurchaseSummary(id, 10, TODAY, 1.0,
            Currency.EUR, null), 10, TODAY)).isEqualTo(1.0);
        assertThat(ProductService.usage(new ProductPurchaseSummary(id, 10, TODAY.minusDays(90),
            1.0, Currency.EUR, null), 10, TODAY)).isCloseTo(0.75, within(1e-9));
        assertThat(ProductService.usage(new ProductPurchaseSummary(id, 1, null, 1.0,
            Currency.EUR, null), 1, TODAY)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void findProductByNameFuzzyReturnsTheBestMatchOrNull() {
        Product milk = product("Прясно мляко");
        Product yoghurt = product("Кисело мляко");
        when(productRepository.getProductsForSearch()).thenReturn(List.of(yoghurt, milk));

        assertThat(service.findProductByNameFuzzy("мляко прясно")).isSameAs(milk);
        assertThat(service.findProductByNameFuzzy("хляб")).isNull();
        assertThat(service.findProductByNameFuzzy("")).isNull();
    }
}
