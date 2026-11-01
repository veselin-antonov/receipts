package dev.vasoft.homeapp.receipts.barcodes.services;

import dev.vasoft.homeapp.receipts.barcodes.api.response.ResBarcodeProduct;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Barcode to product links. A barcode identifies one product in the shared
 * catalog, as products themselves are shared; nothing here reads or writes a
 * purchase.
 *
 * <p>Writes are idempotent so that an offline client (ADR-0005) can queue them
 * and replay them when it is back online: linking a barcode the product already
 * has, or unlinking one it does not have, succeeds and changes nothing.
 *
 * <p>Works on the {@code products} collection through {@link MongoTemplate}
 * with field-level updates, so it never rewrites a product's other fields.
 */
@Service
public class BarcodeService {

    static final String FIELD = "barcodes";

    private final MongoTemplate mongoTemplate;

    public BarcodeService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ResBarcodeProduct findByBarcode(String rawBarcode) {
        String barcode = Gtin.normalize(rawBarcode);
        Product product = mongoTemplate.findOne(byBarcode(barcode), Product.class);
        if (product == null) {
            throw new BarcodeNotLinkedException(barcode);
        }
        return toResponse(product);
    }

    public ResBarcodeProduct link(String productId, String rawBarcode) {
        String barcode = Gtin.normalize(rawBarcode);
        ObjectId id = parseId(productId);

        Product owner = mongoTemplate.findOne(byBarcode(barcode), Product.class);
        if (owner != null && !id.equals(owner.getId())) {
            throw new BarcodeAlreadyLinkedException(barcode, owner.getId());
        }
        if (owner == null) {
            try {
                long matched = mongoTemplate.updateFirst(byId(id),
                    new Update().addToSet(FIELD, barcode), Product.class).getMatchedCount();
                if (matched == 0) {
                    throw new ProductNotFoundException(productId);
                }
            } catch (DuplicateKeyException e) {
                // Another request linked it to some product in between; the
                // unique index refused this one. Report whoever has it now.
                Product winner = mongoTemplate.findOne(byBarcode(barcode), Product.class);
                if (winner == null || !id.equals(winner.getId())) {
                    throw new BarcodeAlreadyLinkedException(barcode,
                        winner == null ? null : winner.getId());
                }
            }
        }
        return toResponse(load(id, productId));
    }

    public ResBarcodeProduct unlink(String productId, String rawBarcode) {
        String barcode = Gtin.normalize(rawBarcode);
        ObjectId id = parseId(productId);

        mongoTemplate.updateFirst(byId(id), new Update().pull(FIELD, barcode), Product.class);
        // Never leave an empty list behind: products without barcodes keep the
        // field absent, like every product created before barcodes existed
        mongoTemplate.updateFirst(
            new Query(Criteria.where("_id").is(id).and(FIELD).size(0)),
            new Update().unset(FIELD), Product.class);
        return toResponse(load(id, productId));
    }

    private Product load(ObjectId id, String productId) {
        Product product = mongoTemplate.findById(id, Product.class);
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }
        return product;
    }

    private static ObjectId parseId(String productId) {
        if (productId == null || !ObjectId.isValid(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return new ObjectId(productId);
    }

    private static Query byBarcode(String barcode) {
        return new Query(Criteria.where(FIELD).is(barcode));
    }

    private static Query byId(ObjectId id) {
        return new Query(Criteria.where("_id").is(id));
    }

    static ResBarcodeProduct toResponse(Product product) {
        List<String> barcodes = product.getBarcodes() == null ? List.of()
            : List.copyOf(product.getBarcodes());
        return new ResBarcodeProduct(product.getId().toHexString(),
            product.getCanonicalName(), product.getIconID(), barcodes);
    }
}
