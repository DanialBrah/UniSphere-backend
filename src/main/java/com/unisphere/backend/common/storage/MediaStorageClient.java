package com.unisphere.backend.common.storage;

import java.io.InputStream;

public interface MediaStorageClient {

    void ensureBucketExists(String bucket);

    String presignPutUrl(String bucket, String key, String contentType, int expiryMinutes);

    String presignGetUrl(String bucket, String key, int expiryMinutes);

    void putObject(String bucket, String key, String contentType, InputStream inputStream, long contentLength);

    void deleteObject(String bucket, String key);
}
