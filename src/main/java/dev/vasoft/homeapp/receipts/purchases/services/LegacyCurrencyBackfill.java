package dev.vasoft.homeapp.receipts.purchases.services;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Stores {@code currency: BGN} on every purchase recorded before currency was
 * part of the model (SPEC §9.5). Runs at bean initialisation, before the web
 * server accepts requests, so nothing is ever served from a row without one.
 * Idempotent: once every row has a currency it matches nothing.
 *
 * <p>This is the one place a missing currency is resolved, and it refuses to
 * guess. Every legacy row predates Bulgaria's euro adoption; a currency-less
 * row dated on or after it could only come from old code running after the
 * changeover, and could be either currency. Startup fails instead, so it gets
 * a human decision rather than a silent 2x error.
 *
 * <p>That date comparison is only meaningful once every stored date is
 * midnight UTC, which {@link LegacyDataGuard} enforces first.
 */
@Component
@DependsOn("legacyDataGuard")
public class LegacyCurrencyBackfill implements InitializingBean {

    static final LocalDate EURO_ADOPTION = LocalDate.of(2026, 1, 1);

    private static final Logger logger = LoggerFactory.getLogger(LegacyCurrencyBackfill.class);

    private final MongoTemplate mongoTemplate;

    public LegacyCurrencyBackfill(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        long ambiguous = mongoTemplate.count(
            query(where("currency").is(null).and("date").gte(EURO_ADOPTION)), Purchase.class);
        if (ambiguous > 0) {
            throw new IllegalStateException(ambiguous + " purchases dated on or after "
                + EURO_ADOPTION + " have no currency, and could be BGN or EUR. "
                + "Set currency on them by hand, then restart.");
        }

        long tagged = mongoTemplate.updateMulti(query(where("currency").is(null)),
            Update.update("currency", Currency.BGN), Purchase.class).getModifiedCount();
        if (tagged > 0) {
            logger.info("Recorded currency BGN on {} legacy purchases", tagged);
        }
    }
}
