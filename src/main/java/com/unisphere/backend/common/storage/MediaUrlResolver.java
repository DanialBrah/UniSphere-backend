package com.unisphere.backend.common.storage;

import com.unisphere.backend.config.ObjectStorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Service;

/**
 * Turns a stored object key into a URL a browser can actually fetch.
 *
 * Media is persisted as a bare key (e.g. {@code posts/9/uuid.jpeg}), never as a resolved URL —
 * the endpoint belongs to whichever provider is active today, so baking it into a row orphans
 * that row on the next provider swap. Reads mint a short-lived presigned GET instead, which is
 * the only path that works across all three supported backends: Garage has no anonymous access
 * at all, and B2 buckets are private, so a plain public URL resolves on GCS alone.
 *
 * Signing is deliberately not cached. It is pure local CPU — a few HMAC rounds, no network and no
 * I/O — so a lookup against any out-of-process cache costs more than the work it would save.
 * Signing per read also keeps the revocation window equal to the URL lifetime: a viewer who loses
 * access stops receiving new URLs immediately rather than being served a shared cached one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final MediaStorageClient mediaStorageClient;
    private final ObjectStorageConfig storageConfig;

    /**
     * @param key bare object key; a full URL is passed through untouched so rows written before
     *            the key migration keep whatever behaviour they had rather than breaking outright.
     * @return a presigned GET URL, or null if the key is absent or presigning fails
     */
    @Named("toViewableUrl")
    public String toViewableUrl(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("http://") || key.startsWith("https://")) return key;

        try {
            return mediaStorageClient.presignGetUrl(
                    storageConfig.getBucket().getPosts(),
                    key,
                    storageConfig.getPresignGetExpiryMinutes());
        } catch (Exception ex) {
            log.warn("Presigned GET failed for key '{}': {}", key, ex.getMessage());
            return null;
        }
    }
}
