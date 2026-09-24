package dev.vasoft.homeapp.receipts.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.MongoConverterConfigurationAdapter;

@Configuration
public class MongoConfig {

    /**
     * Stores every date in UTC regardless of the JVM's timezone.
     *
     * <p>Spring Data's own java.time conversion goes through the system default
     * zone, so a {@code LocalDate} of 2026-09-23 saved from a JVM in
     * Europe/Sofia was stored as 2026-09-22T21:00Z and read back correctly only
     * by a JVM in the same zone. The MongoDB driver's codecs anchor a
     * {@code LocalDate} to midnight UTC in both directions, so a purchase date is
     * the same stored value however the server is started. That leaves the
     * JVM's zone to affect only log timestamps.
     */
    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(
            MongoConverterConfigurationAdapter::useNativeDriverJavaTimeCodecs);
    }
}
