package com.unisphere.backend.commerce.services.service;

import com.unisphere.backend.commerce.services.dto.request.ServiceMediaPresignRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceMediaPresignResponse;
import com.unisphere.backend.commerce.services.dto.response.ServiceMediaUploadResponse;
import com.unisphere.backend.common.exception.MediaUploadException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaStorageClient;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.config.ObjectStorageConfig;
import com.unisphere.backend.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Upload plumbing for a service listing's single portfolio image. Near-verbatim copy of
 * {@code JobApplicationMediaService}, image-only (jpg/jpeg/png/webp — a showcase image, not a
 * document), same shape as {@code ProjectMediaService}'s cover image.
 *
 * <p>Shares the same <em>bucket</em> as every other module, deliberately: {@code MediaUrlResolver}
 * presigns against {@code object-storage.bucket.posts} unconditionally and takes no bucket
 * parameter, so media in a separate bucket would produce silently broken URLs. Segregation is by
 * key prefix, not by bucket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceMediaService {

    private static final String KEY_PREFIX = "services/";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final MediaStorageClient mediaStorageClient;
    private final MediaUrlResolver mediaUrlResolver;
    private final ObjectStorageConfig storageConfig;

    /** A single showcase image, not a document — smaller cap than jobs.resume-max-size-bytes. */
    @Value("${services.portfolio-image-max-size-bytes:5242880}")
    private long maxSizeBytes;

    // No @PostConstruct ensureBucketExists: MediaService already does it for this same bucket at
    // startup — see EventMediaService's identical note.

    public ServiceMediaPresignResponse presignUpload(ServiceMediaPresignRequest request, User currentUser) {
        String mediaKey = newKey(request.filename(), currentUser);
        String uploadUrl = mediaStorageClient.presignPutUrl(
                bucket(), mediaKey, request.contentType(), storageConfig.getPresignExpiryMinutes());
        return new ServiceMediaPresignResponse(uploadUrl, mediaKey);
    }

    public ServiceMediaUploadResponse uploadFile(MultipartFile file, User currentUser) {
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("Portfolio image exceeds the maximum allowed size of " + maxSizeBytes + " bytes");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String mediaKey = newKey(originalFilename, currentUser);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            mediaStorageClient.putObject(bucket(), mediaKey, contentType, inputStream, file.getSize());
        } catch (Exception ex) {
            throw new MediaUploadException("Failed to upload file to object storage: " + ex.getMessage(), ex);
        }

        return new ServiceMediaUploadResponse(mediaKey, mediaUrlResolver.toViewableUrl(mediaKey));
    }

    public void deleteMedia(String mediaKey, User currentUser) {
        assertOwnedKey(mediaKey, currentUser);
        mediaStorageClient.deleteObject(bucket(), mediaKey);
    }

    /** Rejects any key that isn't under the caller's own services prefix. Called on create/update as well as delete. */
    public void assertOwnedKey(String mediaKey, User currentUser) {
        if (mediaKey == null || !mediaKey.startsWith(KEY_PREFIX + currentUser.getId() + "/")) {
            throw new UnauthorizedActionException("Media key does not belong to you: " + mediaKey);
        }
    }

    private String newKey(String filename, User currentUser) {
        String ext = extractExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + ext);
        }
        return KEY_PREFIX + currentUser.getId() + "/" + UUID.randomUUID() + "." + ext;
    }

    private String bucket() {
        return storageConfig.getBucket().getPosts();
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }
}
