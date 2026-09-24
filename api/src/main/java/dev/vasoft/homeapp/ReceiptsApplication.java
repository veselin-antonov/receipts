package dev.vasoft.homeapp;

import dev.vasoft.homeapp.auth.config.JwtKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(JwtKeyProperties.class)
public class ReceiptsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReceiptsApplication.class, args);
	}
}