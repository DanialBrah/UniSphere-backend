package com.unisphere.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

    private String apiKey;
    private String model = "gemini-2.0-flash";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private int maxHistoryTurns = 20;
    private int cacheTtlHours = 24;
    private int sessionTtlHours = 24;

    @Bean
    public RestClient geminiRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
