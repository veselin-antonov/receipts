package dev.vasoft.homeapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Local runs get their configuration from api/.env, but CI and the other tests
 * use the `test` profile and never read it, so nothing else notices if .env
 * stops loading. spring-dotenv 5.x moved its Boot hook out of `spring-dotenv`
 * into per-Boot-major artifacts: bumping the bare artifact kept every build
 * green while `bootRun` lost all its settings.
 */
class DotenvLoadingTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    @Test
    void dotenvFileIsLoadedIntoTheEnvironment(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".env"), "DOTENV_LOADING_PROBE=loaded\n");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .properties("springdotenv.directory=" + dir)
                .run()) {
            assertThat(ctx.getEnvironment().getProperty("DOTENV_LOADING_PROBE")).isEqualTo("loaded");
        }
    }
}
