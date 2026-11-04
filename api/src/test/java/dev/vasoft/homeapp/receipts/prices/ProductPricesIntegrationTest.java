package dev.vasoft.homeapp.receipts.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.vasoft.homeapp.receipts.prices.api.response.ResPricePurchase;
import dev.vasoft.homeapp.receipts.prices.api.response.ResProductPrices;
import dev.vasoft.homeapp.receipts.prices.services.ProductPricesService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The price history's aggregation against a real MongoDB: the match, the sort,
 * the store join and the mapping of stored documents onto the projection.
 * Purchases are inserted as raw documents, in the shape the purchases
 * collection stores them, so the test exercises the read path alone.
 *
 * <p>Same container setup as {@code MongoIntegrationTest}. Needs Docker. Its
 * application context is a second one in the same JVM, so it turns off the
 * rate limiter and its JCache cache, which would otherwise try to create the
 * JVM-wide {@code rate-limit-buckets} cache a second time.
 */
@SpringBootTest(properties = {"spring.cache.type=none", "bucket4j.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class ProductPricesIntegrationTest {

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
    private ProductPricesService productPricesService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final ObjectId userId = new ObjectId();
    private ObjectId productId;
    private ObjectId lidlId;
    private ObjectId billaId;

    @BeforeEach
    void seed() {
        productId = insert("products", new Document("canonicalName", "Банани")
            .append("normalizedCanonicalName", "банани"));
        lidlId = insert("stores", new Document("canonicalName", "Lidl"));
        billaId = insert("stores", new Document("canonicalName", "Billa"));
    }

    private ObjectId insert(String collection, Document document) {
        ObjectId id = new ObjectId();
        mongoTemplate.getCollection(collection).insertOne(document.append("_id", id));
        return id;
    }

    private ObjectId purchase(ObjectId user, LocalDate date, double price, String currency,
        Double quantity, String unit, Boolean assumed, ObjectId store) {
        Document doc = new Document("userId", user)
            .append("product", productId)
            .append("price", price)
            .append("currency", currency)
            .append("date", Date.from(Instant.parse(date + "T00:00:00Z")))
            .append("discountAmount", 0.0);
        if (store != null) {
            doc.append("store", store);
        }
        if (quantity != null) {
            doc.append("quantity", quantity).append("quantityUnit", unit)
                .append("quantityAssumed", assumed);
        }
        return insert("purchases", doc);
    }

    @Test
    void readsAProductsPurchasesWithStoresNewestFirst() {
        purchase(userId, LocalDate.of(2025, 5, 1), 4.87, "BGN", 1.0, "KILOGRAM", true, billaId);
        purchase(userId, LocalDate.of(2026, 10, 1), 3.0876, "EUR", 1.24, "KILOGRAM", false,
            lidlId);
        purchase(userId, LocalDate.of(2026, 3, 1), 1.20, "EUR", 500.0, "GRAM", false, lidlId);

        ResProductPrices prices = productPricesService.getPrices(userId, productId).orElseThrow();

        assertThat(prices.product().name()).isEqualTo("Банани");
        assertThat(prices.unit()).isEqualTo("KILOGRAM");
        assertThat(prices.purchases()).extracting(ResPricePurchase::date).containsExactly(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2025, 5, 1));
        assertThat(prices.purchases()).extracting(p -> p.store().name())
            .containsExactly("Lidl", "Lidl", "Billa");
        assertThat(prices.stats().last().unitPriceEur()).isCloseTo(2.49, within(1e-9));
        assertThat(prices.stats().lowest().unitPriceEur()).isCloseTo(2.40, within(1e-9));
        assertThat(prices.stats().usualStore().name()).isEqualTo("Lidl");
        assertThat(prices.stats().assumedCount()).isEqualTo(1);
        assertThat(prices.purchases().getLast().quantityAssumed()).isTrue();
        assertThat(prices.purchases().getLast().priceEur()).isCloseTo(4.87 / 1.95583,
            within(1e-9));
    }

    @Test
    void sameDayPurchasesKeepTheOrderTheyWereSavedIn() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        purchase(userId, day, 1.00, "EUR", 1.0, "PIECE", false, lidlId);
        ObjectId later = purchase(userId, day, 2.00, "EUR", 1.0, "PIECE", false, billaId);

        ResProductPrices prices = productPricesService.getPrices(userId, productId).orElseThrow();

        assertThat(prices.stats().last().purchaseId()).isEqualTo(later.toHexString());
    }

    @Test
    void legacyRowsWithoutQuantityAreListedWithoutAUnitPrice() {
        purchase(userId, LocalDate.of(2025, 1, 1), 2.00, "BGN", null, null, null, lidlId);

        ResProductPrices prices = productPricesService.getPrices(userId, productId).orElseThrow();

        assertThat(prices.purchases()).singleElement().satisfies(p -> {
            assertThat(p.quantity()).isNull();
            assertThat(p.unitPriceEur()).isNull();
            assertThat(p.comparable()).isFalse();
        });
        assertThat(prices.stats()).isNull();
    }

    @Test
    void aPurchaseWithoutAStoreIsStillListed() {
        purchase(userId, LocalDate.of(2026, 1, 1), 1.00, "EUR", 1.0, "PIECE", false, null);

        ResProductPrices prices = productPricesService.getPrices(userId, productId).orElseThrow();

        assertThat(prices.purchases()).singleElement()
            .satisfies(p -> assertThat(p.store()).isNull());
    }

    @Test
    void anotherUsersPurchasesAreInvisible() {
        purchase(new ObjectId(), LocalDate.of(2026, 1, 1), 1.00, "EUR", 1.0, "PIECE", false,
            lidlId);

        ResProductPrices prices = productPricesService.getPrices(userId, productId).orElseThrow();

        assertThat(prices.purchases()).isEmpty();
        assertThat(prices.stats()).isNull();
    }

    @Test
    void anUnknownProductIsEmpty() {
        assertThat(productPricesService.getPrices(userId, new ObjectId())).isEmpty();
    }
}
