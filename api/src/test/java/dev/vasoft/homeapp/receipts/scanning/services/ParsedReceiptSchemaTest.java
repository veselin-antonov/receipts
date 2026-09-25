package dev.vasoft.homeapp.receipts.scanning.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The schema generated from {@link ParsedReceipt} is sent to the model with every
 * scan, so a change here is a prompt change (ADR-0007). Spring AI 2.x stopped
 * marking fields required by default; this pins that every field still is.
 */
class ParsedReceiptSchemaTest {

    private final JsonNode schema = JsonMapper.builder().build()
        .readTree(new BeanOutputConverter<>(ParsedReceipt.class).getJsonSchema());

    @Test
    void everyReceiptFieldIsRequired() {
        assertThat(schema.get("required").valueStream().map(JsonNode::asString))
            .containsExactlyInAnyOrder("storeName", "receiptDate", "items");
    }

    @Test
    void everyItemFieldIsRequired() {
        JsonNode item = schema.at("/properties/items/items");

        assertThat(item.get("required").valueStream().map(JsonNode::asString))
            .containsExactlyInAnyOrder("productName", "price", "quantity", "quantityUnit",
                "discountAmount");
    }

    @Test
    void noFieldsBeyondTheSchemaAreAllowed() {
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/properties/items/items/additionalProperties").asBoolean()).isFalse();
    }
}
