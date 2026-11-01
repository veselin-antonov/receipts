package dev.vasoft.homeapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.vasoft.homeapp.receipts.products.api.response.ResProductSearchResult;
import dev.vasoft.homeapp.receipts.products.services.ProductService;
import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseService;
import java.time.LocalDate;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Product search against a real MongoDB, so that the purchase-summary
 * aggregation (group by product, last purchase by date, store lookup) and its
 * mapping onto java types are checked by the server rather than by a mock. Set
 * up like {@link MongoIntegrationTest}. Needs Docker.
 */
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=true")
@ActiveProfiles("test")
@Testcontainers
class ProductSearchIntegrationTest {

    @Container
    static GenericContainer<?> mongo = new GenericContainer<>("mongo:8.2")
        .withExposedPorts(27017)
        .withCommand("--auth")
        .withEnv("MONGO_INITDB_ROOT_USERNAME", "test")
        .withEnv("MONGO_INITDB_ROOT_PASSWORD", "test")
        .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 2));

    @DynamicPropertySource
    static void mongoAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.host", mongo::getHost);
        registry.add("spring.mongodb.port", () -> mongo.getMappedPort(27017));
    }

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private ProductService productService;

    private final ObjectId userId = new ObjectId();

    private void buy(String product, String store, double price, Currency currency,
                     LocalDate date) {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, product, null, store,
            price, currency, date, 1, null, 0));
    }

    @Test
    void searchReturnsTheLastPaidPriceCountAndStore() {
        buy("Айрян Верея", "Billa", 1.10, Currency.EUR, LocalDate.of(2026, 10, 2));
        // Older, so not the last purchase, though saved after it
        buy("Айрян Верея", "Lidl", 1.70, Currency.BGN, LocalDate.of(2025, 6, 1));
        buy("Айрян Верея", "Kaufland", 0.95, Currency.EUR, LocalDate.of(2026, 10, 20));

        List<ResProductSearchResult> results = productService.search(userId, "верея айрян", 0);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Айрян Верея");
            assertThat(r.purchaseCount()).isEqualTo(3);
            assertThat(r.lastPurchase().priceEur()).isCloseTo(0.95, within(1e-12));
            assertThat(r.lastPurchase().date()).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(r.lastPurchase().store().name()).isEqualTo("Kaufland");
        });
    }

    @Test
    void anotherUsersPurchasesDoNotCount() {
        buy("Боза Престиж", "Billa", 1.50, Currency.EUR, LocalDate.of(2026, 10, 2));

        List<ResProductSearchResult> results = productService.search(new ObjectId(), "боза", 0);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Боза Престиж");
            assertThat(r.purchaseCount()).isZero();
            assertThat(r.lastPurchase()).isNull();
        });
    }

    @Test
    void legacyBgnPricesAreConvertedToEur() {
        buy("Лютеница Първомай", "Billa", 3.91166, Currency.BGN, LocalDate.of(2025, 3, 1));

        ResProductSearchResult result = productService.search(userId, "lyutenitsa", 0).getFirst();

        assertThat(result.name()).isEqualTo("Лютеница Първомай");
        assertThat(result.lastPurchase().priceEur()).isCloseTo(2.0, within(1e-9));
    }
}
