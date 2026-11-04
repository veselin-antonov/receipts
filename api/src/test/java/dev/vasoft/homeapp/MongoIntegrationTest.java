package dev.vasoft.homeapp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import dev.vasoft.homeapp.receipts.purchases.services.LegacyQuantityMigration;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseService;
import dev.vasoft.homeapp.receipts.common.api.response.ResPage;
import dev.vasoft.homeapp.receipts.scanning.services.ReceiptScanService;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

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
 * The container and context are shared with every other whole-application
 * test; see {@link MongoTestSupport}.
 *
 * <p>Needs Docker. Runs in api-checks on every api change.
 */
class MongoIntegrationTest extends MongoTestSupport {

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ReceiptScanService receiptScanService;

    private final ObjectId userId = new ObjectId();

    @Test
    void purchaseRoundTripsThroughTheDatabase() {
        ReqPurchase manualEntry = new ReqPurchase(null, "Прясно мляко", null, "Billa", 3.29,
            null, LocalDate.of(2026, 9, 23), 1.0, QuantityUnit.PIECE, 0);

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
            1.20, Currency.EUR, LocalDate.of(2026, 9, 24), 1.0, QuantityUnit.PIECE, 0));

        assertThat(purchaseService.getPurchasesPage(new ObjectId(), 0, 10, "").contents())
            .isEmpty();
    }

    @Test
    void calendarDatesAreStoredAsMidnightUtc() {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Кафе", null, "Kaufland",
            7.49, Currency.EUR, LocalDate.of(2026, 3, 1), 1.0, QuantityUnit.PIECE, 0));

        Document raw = mongoTemplate.getCollection("purchases")
            .find(new Document("userId", userId)).first();

        assertThat(raw).isNotNull();
        assertThat(raw.getDate("date").toInstant()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    void searchFindsPurchasesByStoreName() {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Сирене", null, "Billa",
            6.10, Currency.EUR, LocalDate.of(2026, 9, 20), 1.0, QuantityUnit.PIECE, 0));

        assertThat(purchaseService.getPurchasesPage(userId, 0, 10, "billa").contents())
            .hasSize(1);
    }

    @Test
    void searchFindsPurchasesByProductNameAndIgnoresOthers() {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Прясно мляко", null, "Lidl",
            2.10, Currency.EUR, LocalDate.of(2026, 9, 21), 1.0, QuantityUnit.PIECE, 0));
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Хляб", null, "Lidl",
            1.20, Currency.EUR, LocalDate.of(2026, 9, 21), 1.0, QuantityUnit.PIECE, 0));

        ResPage<ResPurchase> page = purchaseService.getPurchasesPage(userId, 0, 10, "МЛЯКО");

        assertThat(page.contents()).singleElement()
            .satisfies(p -> assertThat(p.product().name()).isEqualTo("Прясно мляко"));
    }

    @Test
    void searchTreatsTheQueryAsLiteralText() {
        purchaseService.registerPurchase(userId, new ReqPurchase(null, "Кашкавал", null, "Billa",
            9.80, Currency.EUR, LocalDate.of(2026, 9, 22), 1.0, QuantityUnit.PIECE, 0));

        // Unbalanced regex syntax must neither throw nor match everything
        assertThat(purchaseService.getPurchasesPage(userId, 0, 10, "(").contents()).isEmpty();
        assertThat(purchaseService.getPurchasesPage(userId, 0, 10, ".*").contents()).isEmpty();
    }

    @Test
    void quantityRoundTripsFromScanSubmitToTheRead() {
        // What the scan review sends for "2 x 1,45" and a weighed line (SPEC F2.0)
        receiptScanService.submitPurchases(userId, List.of(
            new ReqPurchase(null, "Кисело мляко", null, "Lidl", 2.90, Currency.EUR,
                LocalDate.of(2026, 10, 2), 2.0, QuantityUnit.PIECE, 0),
            new ReqPurchase(null, "Банани", null, "Lidl", 1.85, Currency.EUR,
                LocalDate.of(2026, 10, 2), 0.742, QuantityUnit.KILOGRAM, 0)));

        assertThat(purchaseService.getPurchasesPage(userId, 0, 10, "").contents())
            .extracting(ResPurchase::quantity, ResPurchase::quantityUnit,
                ResPurchase::quantityAssumed)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(2.0, QuantityUnit.PIECE, false),
                org.assertj.core.groups.Tuple.tuple(0.742, QuantityUnit.KILOGRAM, false));

        Document raw = mongoTemplate.getCollection("purchases")
            .find(new Document("userId", userId).append("quantityUnit", "KILOGRAM")).first();
        assertThat(raw).isNotNull();
        assertThat(raw.getDouble("quantity")).isEqualTo(0.742);
        assertThat(raw.getBoolean("quantityAssumed")).isFalse();
    }

    @Test
    void legacyQuantityMigrationProposesFirstAndAppliesOnlyTheReviewedFile(@TempDir Path dir)
        throws Exception {
        ObjectId onions = insertProduct("Лук (цена за кг)");
        ObjectId salts = insertProduct("Medix соли 1.5кг");
        ObjectId recent = insertLegacyPurchase(onions, 3.49);
        insertLegacyPurchase(onions, 3.29);
        ObjectId salt = insertLegacyPurchase(salts, 5.10);
        Path units = dir.resolve("units.tsv");
        Path proposal = dir.resolve("units.proposed.tsv");

        // Dry run: a proposal, nothing written
        new LegacyQuantityMigration(mongoTemplate, units).afterPropertiesSet();

        assertThat(proposal).exists();
        assertThat(units).doesNotExist();
        assertThat(rawPurchase(recent)).doesNotContainKey("quantity");
        List<String> rows = Files.readAllLines(proposal).stream()
            .filter(l -> !l.startsWith("#")).toList();
        assertThat(rows).containsExactly(
            "KILOGRAM\t2\t" + onions.toHexString() + "\tЛук (цена за кг)\tname says per kg",
            "PIECE\t1\t" + salts.toHexString() + "\tMedix соли 1.5кг"
                + "\tname states a pack size; one pack = one piece");

        // Reviewed: the salts were really priced per kg
        Files.writeString(units, Files.readString(proposal)
            .replace("PIECE\t1\t" + salts.toHexString(), "KILOGRAM\t1\t" + salts.toHexString()));
        new LegacyQuantityMigration(mongoTemplate, units).afterPropertiesSet();

        Document onion = rawPurchase(recent);
        assertThat(onion.getDouble("quantity")).isEqualTo(1.0);
        assertThat(onion.getString("quantityUnit")).isEqualTo("KILOGRAM");
        assertThat(onion.getBoolean("quantityAssumed")).isTrue();
        assertThat(onion.getDouble("price")).isEqualTo(3.49);
        assertThat(rawPurchase(salt).getString("quantityUnit")).isEqualTo("KILOGRAM");
        assertThat(mongoTemplate.getCollection("products")
            .find(new Document("_id", onions)).first().getString("canonicalName"))
            .isEqualTo("Лук (цена за кг)");
        assertThat(purchaseService.getPurchasesPage(userId, 0, 10, "").contents())
            .allSatisfy(p -> assertThat(p.quantityAssumed()).isTrue());

        // Idempotent: a second run finds nothing to do
        new LegacyQuantityMigration(mongoTemplate, units).afterPropertiesSet();
        assertThat(rawPurchase(recent).getDouble("quantity")).isEqualTo(1.0);
    }

    @Test
    void legacyQuantityMigrationRefusesAReviewedFileThatMissesAProduct(@TempDir Path dir)
        throws Exception {
        ObjectId product = insertProduct("Хляб Добруджа");
        ObjectId purchase = insertLegacyPurchase(product, 1.20);
        Path units = dir.resolve("units.tsv");
        Files.writeString(units, "# reviewed, but empty\n");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new LegacyQuantityMigration(mongoTemplate, units).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(product.toHexString());
        assertThat(rawPurchase(purchase)).doesNotContainKey("quantity");

        mongoTemplate.getCollection("purchases").deleteOne(new Document("_id", purchase));
    }

    private ObjectId insertProduct(String name) {
        ObjectId id = new ObjectId();
        mongoTemplate.getCollection("products")
            .insertOne(new Document("_id", id).append("canonicalName", name));
        return id;
    }

    /** A purchase as the old app stored it: no quantity, unit or flag. */
    private ObjectId insertLegacyPurchase(ObjectId product, double price) {
        ObjectId store = new ObjectId();
        mongoTemplate.getCollection("stores")
            .insertOne(new Document("_id", store).append("canonicalName", "Kaufland"));
        ObjectId id = new ObjectId();
        mongoTemplate.getCollection("purchases").insertOne(new Document("_id", id)
            .append("userId", userId)
            .append("product", product)
            .append("price", price)
            .append("currency", "BGN")
            .append("date", java.util.Date.from(Instant.parse("2025-06-01T00:00:00Z")))
            .append("store", store)
            .append("discountAmount", 0.0));
        return id;
    }

    private Document rawPurchase(ObjectId id) {
        return mongoTemplate.getCollection("purchases").find(new Document("_id", id)).first();
    }
}
