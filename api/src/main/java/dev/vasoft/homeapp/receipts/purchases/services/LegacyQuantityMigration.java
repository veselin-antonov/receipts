package dev.vasoft.homeapp.receipts.purchases.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import dev.vasoft.homeapp.receipts.purchases.services.LegacyQuantityUnits.Guess;
import dev.vasoft.homeapp.receipts.purchases.services.LegacyQuantityUnits.Row;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Gives every purchase recorded before quantities existed (D1, Q2) a
 * quantity: 1 in its product's unit, so that its stored price, which was the
 * unit price, stays the unit price. Each such row is marked
 * {@code quantityAssumed}, so it can always be told apart from a recorded
 * quantity. Product names are left alone.
 *
 * <p>The unit per product is a human decision, so this runs in two steps,
 * like {@link LegacyCurrencyBackfill} at startup:
 * <ol>
 *   <li><b>Dry run.</b> With no reviewed file, it writes a proposal (a unit
 *       per product, guessed from the name by {@link LegacyQuantityUnits}) to
 *       {@code <units-file>.proposed.tsv}, logs what it would do, and writes
 *       nothing to the database. The app starts normally; unmigrated rows are
 *       served with a null quantity.</li>
 *   <li><b>Apply.</b> Once the reviewed file exists at {@code units-file}, it
 *       logs the same report and applies it. Every product with an unmigrated
 *       purchase must be in the file, or startup fails rather than guessing.</li>
 * </ol>
 * Idempotent: once every row has a quantity it matches nothing, finds no
 * file, and writes nothing.
 *
 * <p>The file path is the {@code receipts.legacy-quantity.units-file}
 * property, relative to the working directory: {@code api/} under
 * {@code make dev}. It holds product names, so it stays out of git
 * ({@code .migrated/} is ignored).
 */
@Component
@DependsOn("legacyCurrencyBackfill")
public class LegacyQuantityMigration implements InitializingBean {

    static final double ASSUMED_QUANTITY = 1.0;

    private static final Logger logger = LoggerFactory.getLogger(LegacyQuantityMigration.class);

    /** Matches a missing quantity as well as an explicit null. */
    private static final Bson WITHOUT_QUANTITY = Filters.eq("quantity", null);

    private final MongoTemplate mongoTemplate;
    private final Path unitsFile;
    private final Path proposalFile;

    public LegacyQuantityMigration(MongoTemplate mongoTemplate,
        @Value("${receipts.legacy-quantity.units-file:.migrated/legacy-quantity-units.tsv}")
        Path unitsFile) {
        this.mongoTemplate = mongoTemplate;
        this.unitsFile = unitsFile;
        this.proposalFile = proposalPathFor(unitsFile);
    }

    static Path proposalPathFor(Path unitsFile) {
        String name = unitsFile.getFileName().toString();
        String stem = name.endsWith(".tsv") ? name.substring(0, name.length() - 4) : name;
        return unitsFile.resolveSibling(stem + ".proposed.tsv");
    }

    @Override
    public void afterPropertiesSet() {
        try {
            run();
        } catch (IOException e) {
            throw new UncheckedIOException("Legacy quantity migration could not use "
                + unitsFile.toAbsolutePath(), e);
        }
    }

    private void run() throws IOException {
        MongoCollection<Document> purchases = mongoTemplate.getCollection(
            mongoTemplate.getCollectionName(Purchase.class));

        if (purchases.countDocuments(WITHOUT_QUANTITY) == 0) {
            return;
        }

        Map<ObjectId, Long> perProduct = new LinkedHashMap<>();
        long withoutProduct = 0;
        for (Document group : purchases.aggregate(List.of(
            Aggregates.match(WITHOUT_QUANTITY),
            Aggregates.group("$product", Accumulators.sum("purchases", 1))))) {
            Object product = group.get("_id");
            long count = ((Number) group.get("purchases")).longValue();
            if (product instanceof ObjectId id) {
                perProduct.put(id, count);
            } else {
                withoutProduct += count;
            }
        }

        Map<ObjectId, String> names = productNames(perProduct.keySet());
        List<Row> proposal = new ArrayList<>();
        perProduct.forEach((id, count) -> {
            String name = names.get(id);
            Guess guess = LegacyQuantityUnits.guess(name);
            proposal.add(new Row(id, name, count, guess.unit(), guess.reason()));
        });

        if (!Files.exists(unitsFile)) {
            LegacyQuantityUnits.write(proposalFile, proposal,
                unitsFile.getFileName().toString());
            logger.warn("Legacy quantity migration, DRY RUN, nothing written to the database. "
                    + "Would set {}. Proposed unit per product: {}. Review {} and save it as {}, "
                    + "then restart to apply.",
                report(proposal, Map.of()), proposalFile.toAbsolutePath(),
                proposalFile.toAbsolutePath(), unitsFile.toAbsolutePath());
            warnAboutRowsWithoutProduct(withoutProduct);
            return;
        }

        Map<ObjectId, QuantityUnit> approved = LegacyQuantityUnits.read(unitsFile);
        List<Row> missing = proposal.stream()
            .filter(r -> !approved.containsKey(r.productId())).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(missing.size() + " products with legacy purchases "
                + "have no unit in " + unitsFile.toAbsolutePath() + ", for example "
                + missing.stream().limit(5).map(r -> r.productId().toHexString()
                    + " (" + r.name() + ")").collect(Collectors.joining(", "))
                + ". Add them (see " + proposalFile.getFileName() + "), then restart.");
        }

        logger.info("Legacy quantity migration: applying {} from {}",
            report(proposal, approved), unitsFile.toAbsolutePath());

        Map<QuantityUnit, List<ObjectId>> byUnit = new EnumMap<>(QuantityUnit.class);
        for (Row r : proposal) {
            byUnit.computeIfAbsent(approved.get(r.productId()), u -> new ArrayList<>())
                .add(r.productId());
        }
        long updated = 0;
        for (Map.Entry<QuantityUnit, List<ObjectId>> e : byUnit.entrySet()) {
            updated += purchases.updateMany(
                Filters.and(WITHOUT_QUANTITY, Filters.in("product", e.getValue())),
                Updates.combine(
                    Updates.set("quantity", ASSUMED_QUANTITY),
                    Updates.set("quantityUnit", e.getKey().name()),
                    Updates.set("quantityAssumed", true))).getModifiedCount();
        }
        logger.info("Legacy quantity migration: set an assumed quantity on {} purchases",
            updated);
        warnAboutRowsWithoutProduct(withoutProduct);
    }

    private Map<ObjectId, String> productNames(Iterable<ObjectId> ids) {
        List<ObjectId> list = new ArrayList<>();
        ids.forEach(list::add);
        Map<ObjectId, String> names = new HashMap<>();
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(Product.class))
            .find(Filters.in("_id", list))
            .projection(Projections.include("canonicalName"))
            .forEach(d -> names.put(d.getObjectId("_id"), d.getString("canonicalName")));
        return names;
    }

    /** "300 purchases as 1 PIECE (180 products), 40 as 1 KILOGRAM (38 products)". */
    static String report(List<Row> rows, Map<ObjectId, QuantityUnit> approved) {
        Map<QuantityUnit, long[]> totals = new EnumMap<>(QuantityUnit.class);
        for (Row r : rows) {
            QuantityUnit unit = approved.getOrDefault(r.productId(), r.unit());
            long[] t = totals.computeIfAbsent(unit, u -> new long[2]);
            t[0] += r.purchases();
            t[1]++;
        }
        long purchases = totals.values().stream().mapToLong(t -> t[0]).sum();
        return purchases + " legacy purchases to quantity 1 ("
            + totals.entrySet().stream()
                .map(e -> e.getValue()[0] + " as " + e.getKey() + " across "
                    + e.getValue()[1] + " products")
                .collect(Collectors.joining(", ")) + ")";
    }

    private static void warnAboutRowsWithoutProduct(long count) {
        if (count > 0) {
            logger.warn("Legacy quantity migration: {} purchases have no product and are left "
                + "without a quantity", count);
        }
    }
}
