package dev.vasoft.homeapp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseService;
import dev.vasoft.homeapp.receipts.common.api.response.ResPage;
import java.time.Instant;
import java.time.LocalDate;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * The whole application against a real MongoDB, the same major as the dev and
 * deployed stacks. Everything else in the suite mocks the database or loads a
 * slice of the context, so none of it notices when:
 * <ul>
 *   <li>a framework upgrade stops the context starting (Spring AI 1.x on
 *       Boot 4 failed only at startup);</li>
 *   <li>configuration keys are renamed and silently ignored (Boot 4's
 *       {@code spring.mongodb.*});</li>
 *   <li>index creation or a repository query is wrong against a real server.</li>
 * </ul>
 * <p>It connects the way the app does in dev and production: through the
 * {@code spring.mongodb.*} properties, with authentication on, using the test
 * profile's credentials. Only host and port come from the container. A
 * {@code @ServiceConnection} would bypass the properties and miss a rename.
 *
 * <p>Needs Docker. Runs in api-checks on every api change.
 */
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=true")
@ActiveProfiles("test")
@Testcontainers
class MongoIntegrationTest {

    @Container
    static GenericContainer<?> mongo = new GenericContainer<>("mongo:8.2")
        .withExposedPorts(27017)
        .withCommand("--auth")
        // The same root credentials application-test.yaml logs in with
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
    private MongoTemplate mongoTemplate;

    private final ObjectId userId = new ObjectId();

    @Test
    void purchaseRoundTripsThroughTheDatabase() {
        ReqPurchase manualEntry = new ReqPurchase(null, "Прясно мляко", null, "Billa", 3.29,
            null, LocalDate.of(2026, 9, 23), 0, null, 0);

        ResPurchase saved = purchaseService.registerPurchase(userId, manualEntry);
        ResPage<ResPurchase> page = purchaseService.getPurchasesPage(userId, 0, 10, "");

        assertThat(page.contents()).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(saved.id());
            assertThat(p.product().name()).isEqualTo("Прясно мляко");
            assertThat(p.store().name()).isEqualTo("Billa");
            assertThat(p.priceEur()).isEqualTo(3.29);
            assertThat(p.date()).isEqualTo(LocalDate.of(2026, 9, 23));
        });
    }

    @Test
    void purchasesAreScopedToTheirUser() {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Хляб", null, "Lidl",
            1.20, Currency.EUR, LocalDate.of(2026, 9, 24), 1, null, 0));

        assertThat(purchaseService.getPurchasesPage(new ObjectId(), 0, 10, "").contents())
            .isEmpty();
    }

    @Test
    void calendarDatesAreStoredAsMidnightUtc() {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Кафе", null, "Kaufland",
            7.49, Currency.EUR, LocalDate.of(2026, 3, 1), 1, null, 0));

        Document raw = mongoTemplate.getCollection("purchases")
            .find(new Document("userId", userId)).first();

        assertThat(raw).isNotNull();
        assertThat(raw.getDate("date").toInstant()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    @Disabled("M2a: findBySearchQuery filters on productDetails.name / storeDetails.name, "
        + "but the field is canonicalName, so every search returns nothing. Enable with the fix.")
    void searchFindsPurchasesByStoreName() {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Сирене", null, "Billa",
            6.10, Currency.EUR, LocalDate.of(2026, 9, 20), 1, null, 0));

        assertThat(purchaseService.getPurchasesPage(userId, 0, 10, "billa").contents())
            .hasSize(1);
    }
}
