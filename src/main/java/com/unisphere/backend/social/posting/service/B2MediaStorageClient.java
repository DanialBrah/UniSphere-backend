package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.common.exception.MediaUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
@Profile("!ci")
@RequiredArgsConstructor
public class B2MediaStorageClient implements MediaStorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(b -> b.bucket(bucket));
                log.info("Created B2 bucket: {}", bucket);
            } catch (Exception ex) {
                log.error("Failed to create B2 bucket '{}': {}", bucket, ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Could not verify B2 bucket '{}' — credentials may not be configured yet: {}", bucket, ex.getMessage());
        }
    }

    @Override
    public String presignPutUrl(String bucket, String key, String contentType, int expiryMinutes) {
        try {
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .putObjectRequest(b -> b
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                    )
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            throw new MediaUploadException("Could not generate presigned URL", ex);
        }
    }

    @Override
    public void putObject(String bucket, String key, String contentType, InputStream inputStream, long contentLength) {
        try {
            s3Client.putObject(
                    b -> b.bucket(bucket).key(key).contentType(contentType),
                    RequestBody.fromInputStream(inputStream, contentLength)
            );
        } catch (Exception ex) {
            throw new MediaUploadException("Failed to upload file to B2: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            s3Client.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (Exception ex) {
            throw new MediaUploadException("Could not delete media: " + key, ex);
        }
    }
}
