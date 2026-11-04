package dev.vasoft.homeapp.receipts.barcodes.config;

import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import org.bson.Document;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

/**
 * One product per barcode, enforced by the database: a unique index on
 * {@code products.barcodes}.
 *
 * <p>Created here rather than with {@code @Indexed} because it has to be
 * partial. A unique multikey index treats every document without the field, or
 * with an empty list, as holding the same key, so a plain or sparse unique
 * index would refuse the second product that has no barcode. The filter limits
 * it to products that hold at least one string in {@code barcodes}.
 *
 * <p>Runs only where the app creates indexes at all
 * ({@code spring.data.mongodb.auto-index-creation}), so tests that start the
 * context without a database are unaffected. Creating an index that already
 * exists with the same definition is a no-op.
 */
@Component
@ConditionalOnProperty(name = "spring.data.mongodb.auto-index-creation", havingValue = "true")
public class BarcodeIndexInitializer implements SmartInitializingSingleton {

    public static final String INDEX_NAME = "barcodes_unique";

    private final MongoTemplate mongoTemplate;

    public BarcodeIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterSingletonsInstantiated() {
        mongoTemplate.indexOps(Product.class).createIndex(new Index()
            .on("barcodes", Sort.Direction.ASC)
            .named(INDEX_NAME)
            .unique()
            .partial(PartialIndexFilter.of(
                new Document("barcodes", new Document("$type", "string")))));
    }
}
