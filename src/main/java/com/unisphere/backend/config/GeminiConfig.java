package com.unisphere.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

@Getter
@Setter
@Configuration
@Validated
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

    @NotBlank
    private String apiKey;
    private String model = "gemini-2.0-flash";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    @Min(1)
    private int maxHistoryTurns = 20;
    @PositiveOrZero
    private int cacheTtlHours = 24;
    @PositiveOrZero
    private int sessionTtlHours = 24;
    @PositiveOrZero
    private int connectTimeoutMs = 5000;
    @PositiveOrZero
    private int readTimeoutMs = 30000;

    @Bean
    @Profile("!ci")
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }
}
