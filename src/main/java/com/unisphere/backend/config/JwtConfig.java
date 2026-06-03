package com.unisphere.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    private String secret;
    private long expiration;
    private long refreshExpiration;

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret must not be empty — set JWT_SECRET in .env or environment variables");
        }

        try {
            byte[] keyBytes = HexFormat.of().parseHex(secret);
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "jwt.secret is too short — must be at least 256 bits (64 hex characters), got "
                        + (keyBytes.length * 8) + " bits");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "jwt.secret is not valid hex: " + e.getMessage());
        }

        if (expiration <= 0) {
            throw new IllegalStateException(
                    "jwt.expiration must be a positive number of milliseconds, got: " + expiration);
        }

        if (refreshExpiration <= 0) {
            throw new IllegalStateException(
                    "jwt.refresh-expiration must be a positive number of milliseconds, got: " + refreshExpiration);
        }
    }
}
