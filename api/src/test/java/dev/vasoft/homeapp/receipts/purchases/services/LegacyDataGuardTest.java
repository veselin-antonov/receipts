package dev.vasoft.homeapp.receipts.purchases.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class LegacyDataGuardTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private void counts(long unanchoredDates, long ownerless) {
        when(mongoTemplate.count(argThat((Query q) -> q != null
                && q.getQueryObject().equals(Document.parse(LegacyDataGuard.DATE_NOT_UTC_MIDNIGHT))),
            eq(Purchase.class))).thenReturn(unanchoredDates);
        when(mongoTemplate.count(argThat((Query q) -> q != null
                && q.getQueryObject().equals(Document.parse(LegacyDataGuard.WITHOUT_OWNER))),
            eq(Purchase.class))).thenReturn(ownerless);
    }

    @Test
    void startsWhenEveryDateIsUtcMidnightAndEveryPurchaseHasAnOwner() {
        counts(0, 0);

        assertThatCode(() -> new LegacyDataGuard(mongoTemplate).afterPropertiesSet())
            .doesNotThrowAnyException();
    }

    @Test
    void refusesSofiaAnchoredDatesThatWouldReadAsThePreviousDay() {
        counts(717, 0);

        assertThatThrownBy(() -> new LegacyDataGuard(mongoTemplate).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("717 purchases have a date that is not midnight UTC")
            .hasMessageContaining("migrate-backups.py");
    }

    @Test
    void refusesOwnerlessPurchasesThatWouldBeInvisible() {
        counts(0, 12);

        assertThatThrownBy(() -> new LegacyDataGuard(mongoTemplate).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("12 purchases have no userId");
    }

    @Test
    void reportsBothProblemsAtOnce() {
        counts(3, 4);

        assertThatThrownBy(() -> new LegacyDataGuard(mongoTemplate).afterPropertiesSet())
            .hasMessageContaining("3 purchases have a date")
            .hasMessageContaining("4 purchases have no userId");
    }
}
