package dev.vasoft.homeapp.receipts.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseMapper;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanResult;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the one wire convention (SPEC §9.1) through the ObjectMapper the app
 * actually uses: dates and times are ISO-8601 in both directions, money is a
 * number, and nothing is pre-formatted for display. Loads only Jackson's
 * auto-configuration, so application.yaml's spring.jackson settings apply and
 * nothing else in the app has to start.
 */
@SpringBootTest(classes = JacksonAutoConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class WireFormatTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void requestDatesAreIso() throws Exception {
        ReqPurchase req = objectMapper.readValue(
            "{\"productName\":\"Мляко\",\"price\":3.29,\"date\":\"2026-09-23\"}", ReqPurchase.class);

        assertThat(req.date()).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    void aMissingDiscountReadsAsZeroAndAMissingQuantityAsNull() throws Exception {
        // Jackson 3 would reject the missing primitive discount unless told
        // otherwise. A missing quantity stays null, so validation rejects it
        // instead of storing a made-up 0 (D1).
        ReqPurchase req = objectMapper.readValue(
            "{\"productName\":\"Мляко\",\"storeName\":\"Billa\",\"price\":3.29,"
                + "\"date\":\"2026-09-23\"}", ReqPurchase.class);

        assertThat(req.quantity()).isNull();
        assertThat(req.quantityUnit()).isNull();
        assertThat(req.discountAmount()).isZero();
    }

    @Test
    void requestQuantityAndUnitAreRead() throws Exception {
        ReqPurchase req = objectMapper.readValue(
            "{\"productName\":\"Банани\",\"storeName\":\"Lidl\",\"price\":1.85,"
                + "\"date\":\"2026-09-23\",\"quantity\":0.742,\"quantityUnit\":\"KILOGRAM\"}",
            ReqPurchase.class);

        assertThat(req.quantity()).isEqualTo(0.742);
        assertThat(req.quantityUnit()).isEqualTo(QuantityUnit.KILOGRAM);
    }

    @Test
    void dayFirstRequestDatesAreRejectedRatherThanGuessed() {
        assertThatThrownBy(() -> objectMapper.readValue(
            "{\"price\":3.29,\"date\":\"23/09/2026\"}", ReqPurchase.class))
            .isInstanceOf(JacksonException.class);
    }

    @Test
    void purchaseResponsesCarryNumbersAndIsoDates() {
        Purchase p = new Purchase(new ObjectId(), new Product(new ObjectId()), 12.65, Currency.BGN,
            LocalDate.of(2025, 10, 3), new Store(new ObjectId()), 0.0, 0.75, QuantityUnit.KILOGRAM);
        p.setId(new ObjectId());

        JsonNode json = objectMapper.valueToTree(PurchaseMapper.toResPurchase(p));

        assertThat(json.get("date").asString()).isEqualTo("2025-10-03");
        assertThat(json.get("priceEur").isNumber()).isTrue();
        assertThat(json.get("priceEur").asDouble()).isEqualTo(12.65 / 1.95583);
        assertThat(json.get("discountAmountEur").isNumber()).isTrue();
        assertThat(json.has("price")).isFalse();
        assertThat(json.get("quantity").asDouble()).isEqualTo(0.75);
        assertThat(json.get("quantityUnit").asString()).isEqualTo("KILOGRAM");
        assertThat(json.get("quantityAssumed").asBoolean()).isFalse();
    }

    @Test
    void scanResultDateIsIso() {
        JsonNode json = objectMapper.valueToTree(
            new ResScanResult(null, "Billa", LocalDate.of(2026, 6, 8), List.of()));

        assertThat(json.get("purchaseDate").asString()).isEqualTo("2026-06-08");
    }

    @Test
    void instantsAreIsoUtc() throws Exception {
        assertThat(objectMapper.writeValueAsString(Instant.parse("2026-09-23T18:15:10Z")))
            .isEqualTo("\"2026-09-23T18:15:10Z\"");
    }
}
