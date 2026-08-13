package com.unisphere.backend.projects.service;

import com.unisphere.backend.common.exception.MediaUploadException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaStorageClient;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.config.ObjectStorageConfig;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.projects.dto.request.ProjectMediaPresignRequest;
import com.unisphere.backend.projects.dto.response.ProjectMediaPresignResponse;
import com.unisphere.backend.projects.dto.response.ProjectMediaUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Upload plumbing for a project's cover image. Near-verbatim copy of
 * {@code JobApplicationMediaService}, image-only (jpg/png/webp, not a document).
 *
 * <p>Shares the same <em>bucket</em> as every other module, deliberately: {@code MediaUrlResolver}
 * presigns against {@code object-storage.bucket.posts} unconditionally and takes no bucket
 * parameter, so media in a separate bucket would produce silently broken URLs. Segregation is by
 * key prefix, not by bucket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMediaService {

    private static final String KEY_PREFIX = "projects/";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final MediaStorageClient mediaStorageClient;
    private final MediaUrlResolver mediaUrlResolver;
    private final ObjectStorageConfig storageConfig;

    @Value("${projects.cover-image-max-size-bytes:5242880}")
    private long maxSizeBytes;

    // No @PostConstruct ensureBucketExists: MediaService already does it for this same bucket at
    // startup — see JobApplicationMediaService's identical note.

    public ProjectMediaPresignResponse presignUpload(ProjectMediaPresignRequest request, User currentUser) {
        String mediaKey = newKey(request.filename(), currentUser);
        String uploadUrl = mediaStorageClient.presignPutUrl(
                bucket(), mediaKey, request.contentType(), storageConfig.getPresignExpiryMinutes());
        return new ProjectMediaPresignResponse(uploadUrl, mediaKey);
    }

    public ProjectMediaUploadResponse uploadFile(MultipartFile file, User currentUser) {
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("Cover image exceeds the maximum allowed size of " + maxSizeBytes + " bytes");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String mediaKey = newKey(originalFilename, currentUser);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            mediaStorageClient.putObject(bucket(), mediaKey, contentType, inputStream, file.getSize());
        } catch (Exception ex) {
            throw new MediaUploadException("Failed to upload file to object storage: " + ex.getMessage(), ex);
        }

        return new ProjectMediaUploadResponse(mediaKey, mediaUrlResolver.toViewableUrl(mediaKey));
    }

    public void deleteMedia(String mediaKey, User currentUser) {
        assertOwnedKey(mediaKey, currentUser);
        mediaStorageClient.deleteObject(bucket(), mediaKey);
    }

    /** Rejects any key that isn't under the caller's own projects prefix. Called on create/update as well as delete. */
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
