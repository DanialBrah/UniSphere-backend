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
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
@Profile("!ci")
@RequiredArgsConstructor
public class S3MediaStorageClient implements MediaStorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(b -> b.bucket(bucket));
                log.info("Created object storage bucket: {}", bucket);
            } catch (Exception ex) {
                log.error("Failed to create object storage bucket '{}': {}", bucket, ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Could not verify object storage bucket '{}' — credentials may not be configured yet: {}", bucket, ex.getMessage());
        }
    }

    @Override
    public String presignPutUrl(String bucket, String key, String contentType, int expiryMinutes) {
        try {
            // Content-Type is deliberately NOT bound into the presigned request: doing so makes the
            // signature itself require the eventual PUT to carry that exact header, and the actor
            // signing (this backend) is not the actor sending (the browser) — any mismatch or
            // omission on the client side fails signature validation with a cryptic "signed header
            // content-type is not present" error. Leaving it unbound lets the browser PUT with
            // whatever Content-Type it naturally sends (or none); the object still gets stored with
            // whatever Content-Type header the actual request carries, presigned or not.
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .putObjectRequest(b -> b
                            .bucket(bucket)
                            .key(key)
                    )
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            throw new MediaUploadException("Could not generate presigned URL", ex);
        }
    }

    @Override
    public String presignGetUrl(String bucket, String key, int expiryMinutes) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .getObjectRequest(b -> b.bucket(bucket).key(key))
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            throw new MediaUploadException("Could not generate presigned GET URL", ex);
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
            throw new MediaUploadException("Failed to upload file to object storage: " + ex.getMessage(), ex);
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
