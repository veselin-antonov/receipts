package dev.vasoft.homeapp.receipts.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.MongoClientSettings;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * A purchase date must be stored as midnight UTC of that day, whatever zone
 * the JVM runs in. Runs the real mapping and the real driver codec, with the
 * JVM's default zone forced to Europe/Sofia.
 */
class MongoDateStorageTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);
    private static final Instant MIDNIGHT_UTC = Instant.parse("2026-09-23T00:00:00Z");

    private TimeZone originalZone;

    @BeforeEach
    void runInSofia() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Sofia"));
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private static MappingMongoConverter converter(MongoCustomConversions conversions) {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return converter;
    }

    /** Maps the purchase, then encodes it exactly as the driver would send it. */
    private static BsonDocument stored(MappingMongoConverter converter) {
        Purchase purchase = new Purchase(new ObjectId(), null, 3.29, Currency.EUR, DAY, null, 0.0);
        Document document = new Document();
        converter.write(purchase, document);
        BsonDocument bson = new BsonDocument();
        MongoClientSettings.getDefaultCodecRegistry().get(Document.class)
            .encode(new BsonDocumentWriter(bson), document, EncoderContext.builder().build());
        return bson;
    }

    @Test
    void aPurchaseDateIsStoredAsMidnightUtcEvenInSofia() {
        BsonDocument bson = stored(converter(new MongoConfig().mongoCustomConversions()));

        assertThat(Instant.ofEpochMilli(bson.getDateTime("date").getValue())).isEqualTo(MIDNIGHT_UTC);
    }

    @Test
    void itReadsBackAsTheSameDay() {
        MappingMongoConverter converter = converter(new MongoConfig().mongoCustomConversions());
        BsonDocument bson = stored(converter);

        Document decoded = MongoClientSettings.getDefaultCodecRegistry().get(Document.class)
            .decode(new BsonDocumentReader(bson), DecoderContext.builder().build());

        assertThat(converter.read(Purchase.class, decoded).getDate()).isEqualTo(DAY);
    }

    @Test
    void springDatasDefaultWouldHaveStoredThePreviousEvening() {
        // The control: without MongoConfig this is what a Sofia JVM stored,
        // and it is what was observed on the dev stack before the change.
        BsonDocument bson = stored(converter(new MongoCustomConversions(List.of())));

        assertThat(Instant.ofEpochMilli(bson.getDateTime("date").getValue()))
            .isEqualTo(Instant.parse("2026-09-22T21:00:00Z"));
    }
}
