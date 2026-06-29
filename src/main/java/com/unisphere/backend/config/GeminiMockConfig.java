package com.unisphere.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("ci")
public class GeminiMockConfig {

    private static final String CANNED_RESPONSE = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "This is a mocked Gemini response (CI profile active)." }
                    ]
                  }
                }
              ]
            }
            """;

    @Bean
    public RestClient geminiRestClient() {
        return RestClient.builder()
                .requestInterceptor((request, body, execution) -> new ClientHttpResponse() {
                    @Override
                    public HttpStatusCode getStatusCode() {
                        return HttpStatus.OK;
                    }

                    @Override
                    public String getStatusText() {
                        return "OK";
                    }

                    @Override
                    public void close() {
                    }

                    @Override
                    public InputStream getBody() {
                        return new ByteArrayInputStream(CANNED_RESPONSE.getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        return headers;
                    }
                })
                .build();
    }
}
