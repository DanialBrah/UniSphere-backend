package com.unisphere.backend.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConfigurationProperties(prefix = "object-storage")
@Validated
@Getter
@Setter
public class ObjectStorageConfig {

    /** Full S3-compatible endpoint, e.g. https://s3.us-west-004.backblazeb2.com or https://storage.googleapis.com */
    @NotBlank(message = "object-storage.endpoint must not be blank")
    private String endpoint;

    /** Region matching the endpoint */
    @NotBlank(message = "object-storage.region must not be blank")
    private String region;

    /** S3-compatible access key ID */
    private String accessKeyId;

    /** S3-compatible secret access key */
    private String secretAccessKey;

    private Bucket bucket = new Bucket();
    private int presignExpiryMinutes = 5;

    /**
     * Lifetime of a presigned GET. Media URLs are embedded in feed responses, so this has to
     * outlive however long a client may hold that JSON — too short and cached responses come back
     * with URLs that have already expired.
     */
    private int presignGetExpiryMinutes = 60;

    @Getter
    @Setter
    public static class Bucket {
        private String posts = "posts";
    }

    // No resolveMediaUrl() here by design: a plain public URL only resolves on GCS. Garage rejects
    // anonymous access outright and B2 buckets are private, so reads go through
    // MediaUrlResolver (presigned GET), which works on all three. See changeset 012.

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }

    private S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
    }

    @Bean
    @Profile("!ci")
    S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials())
                .region(Region.of(region))
                .serviceConfiguration(s3Configuration())
                .build();
    }

    @Bean
    @Profile("!ci")
    S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials())
                .region(Region.of(region))
                .serviceConfiguration(s3Configuration())
                .build();
    }
}
