package com.unisphere.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConfigurationProperties(prefix = "backblaze.b2")
@Getter
@Setter
public class B2Config {

    /** Full S3-compatible endpoint, e.g. https://s3.us-west-004.backblazeb2.com */
    private String endpoint;

    /** Region matching the endpoint, e.g. us-west-004 */
    private String region;

    /** Backblaze Application Key ID (used as S3 access key) */
    private String keyId;

    /** Backblaze Application Key (used as S3 secret key) */
    private String applicationKey;

    private Bucket bucket = new Bucket();
    private int presignExpiryMinutes = 5;

    @Getter
    @Setter
    public static class Bucket {
        private String posts = "posts";
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, applicationKey));
    }

    private S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
    }

    @Bean
    S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials())
                .region(Region.of(region))
                .serviceConfiguration(s3Configuration())
                .build();
    }

    @Bean
    S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials())
                .region(Region.of(region))
                .serviceConfiguration(s3Configuration())
                .build();
    }
}
