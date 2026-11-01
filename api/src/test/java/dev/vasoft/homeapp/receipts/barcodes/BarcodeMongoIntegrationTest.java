package dev.vasoft.homeapp.receipts.barcodes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.vasoft.homeapp.receipts.barcodes.api.response.ResBarcodeProduct;
import dev.vasoft.homeapp.receipts.barcodes.config.BarcodeIndexInitializer;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeAlreadyLinkedException;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeNotLinkedException;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeService;
import dev.vasoft.homeapp.receipts.barcodes.services.ProductNotFoundException;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Barcode links against a real MongoDB. What a mock cannot show: the partial
 * unique index (many products without a barcode, one product per barcode),
 * {@code $addToSet} idempotence, and that an unlink leaves no empty list.
 *
 * <p>Builds its own {@link MongoTemplate} on the container instead of starting
 * the app: a second full context in the suite's JVM fails on the JCache
 * rate-limit cache {@code MongoIntegrationTest}'s context already registered.
 * The index is created by the real {@link BarcodeIndexInitializer}.
 *
 * <p>Needs Docker. Runs in api-checks on every api change.
 */
@Testcontainers
class BarcodeMongoIntegrationTest {

    private static final String EAN = "3800178226137";
    private static final String UPC_A = "036000291452";

    @Container
    static GenericContainer<?> mongo = new GenericContainer<>("mongo:8.2")
        .withExposedPorts(27017)
        .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1));

    private static MongoClient client;
    private static MongoTemplate mongoTemplate;
    private static BarcodeService barcodeService;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(
            "mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017));
        mongoTemplate = new MongoTemplate(client, "receipts-barcodes-test");
        new BarcodeIndexInitializer(mongoTemplate).afterSingletonsInstantiated();
        barcodeService = new BarcodeService(mongoTemplate);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private Product milk;
    private Product bread;

    @BeforeEach
    void freshProducts() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(),
            Product.class);
        milk = mongoTemplate.save(new Product("Прясно мляко"));
        bread = mongoTemplate.save(new Product("Хляб"));
    }

    private Document raw(Product product) {
        return mongoTemplate.getCollection("products")
            .find(new Document("_id", product.getId())).first();
    }

    @Test
    void theUniqueIndexExistsAndIsPartial() {
        Document index = mongoTemplate.getCollection("products").listIndexes()
            .into(new java.util.ArrayList<>()).stream()
            .filter(i -> BarcodeIndexInitializer.INDEX_NAME.equals(i.getString("name")))
            .findFirst().orElseThrow();

        assertThat(index.getBoolean("unique")).isTrue();
        assertThat(index.get("partialFilterExpression", Document.class)).isNotNull();
    }

    @Test
    void productsWithoutBarcodesDoNotCollide() {
        // Both fixtures lack the field; a third, with an explicit empty list,
        // must not trip the unique index either
        Document empty = new Document("canonicalName", "Яйца").append("barcodes", List.of());
        mongoTemplate.getCollection("products").insertOne(empty);
        mongoTemplate.getCollection("products").insertOne(
            new Document("canonicalName", "Сирене").append("barcodes", List.of()));

        assertThat(mongoTemplate.getCollection("products").countDocuments()).isEqualTo(4);
    }

    @Test
    void linksThenFindsTheProduct() {
        ResBarcodeProduct linked = barcodeService.link(milk.getId().toHexString(), EAN);
        ResBarcodeProduct found = barcodeService.findByBarcode(EAN);

        assertThat(linked.barcodes()).containsExactly(EAN);
        assertThat(found.id()).isEqualTo(milk.getId().toHexString());
        assertThat(found.name()).isEqualTo("Прясно мляко");
    }

    @Test
    void linkingDoesNotTouchTheProductsOtherFields() {
        mongoTemplate.getCollection("products").updateOne(new Document("_id", milk.getId()),
            new Document("$set", new Document("aliases", List.of("Мляко прясно 3%"))));

        barcodeService.link(milk.getId().toHexString(), EAN);

        assertThat(raw(milk).getList("aliases", String.class)).containsExactly("Мляко прясно 3%");
        assertThat(raw(milk).getString("canonicalName")).isEqualTo("Прясно мляко");
    }

    @Test
    void linkingTwiceIsANoOp() {
        barcodeService.link(milk.getId().toHexString(), EAN);
        ResBarcodeProduct again = barcodeService.link(milk.getId().toHexString(), EAN);

        assertThat(again.barcodes()).containsExactly(EAN);
    }

    @Test
    void aProductCanHaveSeveralBarcodes() {
        barcodeService.link(milk.getId().toHexString(), EAN);
        ResBarcodeProduct both = barcodeService.link(milk.getId().toHexString(), UPC_A);

        assertThat(both.barcodes()).containsExactly(EAN, "0" + UPC_A);
    }

    @Test
    void aBarcodeOnAnotherProductIsAConflictNamingIt() {
        barcodeService.link(milk.getId().toHexString(), EAN);

        assertThatThrownBy(() -> barcodeService.link(bread.getId().toHexString(), EAN))
            .isInstanceOfSatisfying(BarcodeAlreadyLinkedException.class,
                e -> assertThat(e.getLinkedProductId()).isEqualTo(milk.getId()));
        assertThat(raw(bread)).doesNotContainKey("barcodes");
    }

    @Test
    void theDatabaseRefusesADuplicateEvenPastTheService() {
        barcodeService.link(milk.getId().toHexString(), EAN);

        assertThatThrownBy(() -> mongoTemplate.getCollection("products").updateOne(
            new Document("_id", bread.getId()),
            new Document("$addToSet", new Document("barcodes", EAN))))
            .isInstanceOf(com.mongodb.MongoWriteException.class);
    }

    @Test
    void upcAAndEan13FindTheSameProduct() {
        barcodeService.link(milk.getId().toHexString(), UPC_A);

        assertThat(barcodeService.findByBarcode("0" + UPC_A).id())
            .isEqualTo(milk.getId().toHexString());
    }

    @Test
    void anUnknownBarcodeIsNotLinked() {
        assertThatThrownBy(() -> barcodeService.findByBarcode(EAN))
            .isInstanceOf(BarcodeNotLinkedException.class);
    }

    @Test
    void linkingToAMissingProductIsNotFound() {
        assertThatThrownBy(() -> barcodeService.link(new ObjectId().toHexString(), EAN))
            .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> barcodeService.link("not-an-id", EAN))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void unlinkingTheLastBarcodeRemovesTheField() {
        barcodeService.link(milk.getId().toHexString(), EAN);

        ResBarcodeProduct after = barcodeService.unlink(milk.getId().toHexString(), EAN);

        assertThat(after.barcodes()).isEmpty();
        assertThat(raw(milk)).doesNotContainKey("barcodes");
        // and the code is free for another product
        barcodeService.link(bread.getId().toHexString(), EAN);
    }

    @Test
    void unlinkingACodeTheProductDoesNotHaveIsANoOp() {
        barcodeService.link(milk.getId().toHexString(), EAN);

        ResBarcodeProduct after = barcodeService.unlink(milk.getId().toHexString(), UPC_A);

        assertThat(after.barcodes()).containsExactly(EAN);
    }
}
