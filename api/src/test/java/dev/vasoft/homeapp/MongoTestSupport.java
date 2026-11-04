package dev.vasoft.homeapp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * The one MongoDB container and the one application context that every
 * whole-application test shares.
 *
 * <p>The suite can hold only one full context per JVM: the rate-limit caches are
 * JCache caches, created in the JVM-wide Caffeine provider, so a second context
 * fails at startup with "Cache rate-limit-buckets already exists". Spring reuses
 * a context only for an identical configuration, so the annotations and the
 * {@code @DynamicPropertySource} live here, and test classes extend this
 * without adding their own.
 *
 * <p>The container is started once, in a static initializer, rather than per
 * class through {@code @Container}: a per-class container would be stopped
 * after the first class, while the cached context kept pointing at its port.
 * Testcontainers' Ryuk sidecar removes it when the JVM exits.
 *
 * <p>It connects the way the app does in dev and production: through the
 * {@code spring.mongodb.*} properties, with authentication on, using the test
 * profile's credentials. Only host and port come from the container. A
 * {@code @ServiceConnection} would bypass the properties and miss a rename.
 */
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=true")
@ActiveProfiles("test")
public abstract class MongoTestSupport {

    static final GenericContainer<?> mongo = new GenericContainer<>("mongo:8.2")
        .withExposedPorts(27017)
        .withCommand("--auth")
        // The same root credentials application-test.yaml logs in with
        .withEnv("MONGO_INITDB_ROOT_USERNAME", "test")
        .withEnv("MONGO_INITDB_ROOT_PASSWORD", "test")
        .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 2));

    static {
        mongo.start();
    }

    @DynamicPropertySource
    static void mongoAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.host", mongo::getHost);
        registry.add("spring.mongodb.port", () -> mongo.getMappedPort(27017));
    }
}
