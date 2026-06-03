package com.unisphere.backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads .env from the project root into the Spring environment before
 * application.properties is resolved. On Render, env vars are injected
 * natively so .env won't exist — ignoreIfMissing() handles that gracefully.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            Map<String, Object> props = new HashMap<>();
            dotenv.entries().forEach(entry -> props.put(entry.getKey(), entry.getValue()));

            if (!props.isEmpty()) {
                // Add with lowest precedence so Render/OS env vars always win
                environment.getPropertySources()
                        .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
            }
        } catch (Exception ignored) {
            // Never fail startup due to .env loading
        }
    }
}
