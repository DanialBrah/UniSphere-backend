package com.unisphere.backend.social.posting.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@Profile("ci")
public class MockMediaStorageClient implements MediaStorageClient {

    @Override
    public void ensureBucketExists(String bucket) {
        log.info("[ci] Skipping bucket existence check for '{}'", bucket);
    }

    @Override
    public String presignPutUrl(String bucket, String key, String contentType, int expiryMinutes) {
        String url = "https://mock-b2.local/" + bucket + "/" + key;
        log.info("[ci] Returning fake presigned URL: {}", url);
        return url;
    }

    @Override
    public void putObject(String bucket, String key, String contentType, InputStream inputStream, long contentLength) {
        log.info("[ci] Skipping real upload of '{}/{}' ({} bytes)", bucket, key, contentLength);
    }

    @Override
    public void deleteObject(String bucket, String key) {
        log.info("[ci] Skipping real delete of '{}/{}'", bucket, key);
    }
}
