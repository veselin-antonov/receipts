package dev.vasoft.homeapp.receipts.purchases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class LegacyCurrencyBackfillTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void tagsEveryCurrencylessPurchaseAsBgn() {
        when(mongoTemplate.count(any(Query.class), eq(Purchase.class))).thenReturn(0L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Purchase.class)))
            .thenReturn(UpdateResult.acknowledged(717, 717L, null));

        new LegacyCurrencyBackfill(mongoTemplate).afterPropertiesSet();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(Purchase.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("currency", null));
        assertThat(update.getValue().getUpdateObject())
            .isEqualTo(new Document("$set", new Document("currency", Currency.BGN)));
    }

    @Test
    void refusesToGuessForRowsFromAfterTheChangeover() {
        when(mongoTemplate.count(any(Query.class), eq(Purchase.class))).thenReturn(3L);

        assertThatThrownBy(() -> new LegacyCurrencyBackfill(mongoTemplate).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("3 purchases dated on or after 2026-01-01");
        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class),
            eq(Purchase.class));
    }

    @Test
    void theAmbiguityCheckLooksOnlyAtCurrencylessRowsFromTheChangeoverOn() {
        when(mongoTemplate.count(any(Query.class), eq(Purchase.class))).thenReturn(0L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Purchase.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        new LegacyCurrencyBackfill(mongoTemplate).afterPropertiesSet();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(query.capture(), eq(Purchase.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("currency", null)
            .append("date", new Document("$gte", LegacyCurrencyBackfill.EURO_ADOPTION)));
    }
}
