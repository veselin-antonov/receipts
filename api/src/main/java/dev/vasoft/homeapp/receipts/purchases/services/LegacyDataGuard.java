package dev.vasoft.homeapp.receipts.purchases.services;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Component;

/**
 * Refuses to start against purchase data that predates the current schema.
 *
 * <p>The app assumes two things that the Jan-2026 backups, and any database
 * the old app wrote, do not satisfy:
 * <ul>
 *   <li>every stored date is midnight UTC of its day. {@code MongoConfig} reads
 *       dates as UTC, so the old app's Sofia-midnight instants (21:00Z/22:00Z)
 *       would silently read as the previous day;</li>
 *   <li>every purchase has a {@code userId}. Reads are scoped by it, so an
 *       ownerless purchase silently disappears.</li>
 * </ul>
 * Both are fixed by {@code scripts/migrate-backups.py} in the receipts repo,
 * which re-anchors dates and assigns the owner. Neither is guessed here: the
 * owner is a decision, and a silent fix-up of dates is how D11 happened.
 * Runs before {@link LegacyCurrencyBackfill}, whose date check depends on it.
 */
@Component
public class LegacyDataGuard implements InitializingBean {

    /** Real dates whose UTC time of day is not exactly midnight. */
    static final String DATE_NOT_UTC_MIDNIGHT = "{ date: { $type: 'date' }, $expr: { $ne: "
        + "[ { $dateToString: { date: '$date', format: '%H:%M:%S.%L', timezone: 'UTC' } }, "
        + "'00:00:00.000' ] } }";

    /** Matches a missing userId as well as an explicit null. */
    static final String WITHOUT_OWNER = "{ userId: null }";

    private final MongoTemplate mongoTemplate;

    public LegacyDataGuard(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        long unanchored = mongoTemplate.count(new BasicQuery(DATE_NOT_UTC_MIDNIGHT), Purchase.class);
        long ownerless = mongoTemplate.count(new BasicQuery(WITHOUT_OWNER), Purchase.class);

        List<String> problems = new ArrayList<>();
        if (unanchored > 0) {
            problems.add(unanchored + " purchases have a date that is not midnight UTC, "
                + "and would read back as the wrong day");
        }
        if (ownerless > 0) {
            problems.add(ownerless + " purchases have no userId, and would be invisible");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Purchase data predates the current schema: "
                + String.join("; ", problems) + ". Migrate it with scripts/migrate-backups.py "
                + "in the receipts repo (see docs/DEV_SETUP.md), then restart.");
        }
    }
}
