package com.unisphere.backend.campus.lostfound.service;

import com.unisphere.backend.campus.lostfound.dto.request.LostFoundMediaPresignRequest;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundMediaPresignResponse;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundMediaUploadResponse;
import com.unisphere.backend.common.exception.MediaUploadException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaStorageClient;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.config.ObjectStorageConfig;
import com.unisphere.backend.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Upload plumbing for lost &amp; found imagery — item photos and claim proof shots. A separate class
 * rather than a reuse of {@code campus.news.service.NewsMediaService} or
 * {@code social.posting.service.MediaService}: both hardcode their own prefix ownership and would
 * reject every lost-found key.
 *
 * <p>It does share the same <em>bucket</em>, deliberately.
 * {@link MediaUrlResolver#toViewableUrl(String)} presigns against {@code object-storage.bucket.posts}
 * unconditionally and takes no bucket parameter, so media in a separate bucket would produce
 * silently broken URLs — the resolver only returns null when <em>signing</em> throws, not when the
 * object is missing. Segregation is by key prefix, not by bucket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LostFoundMediaService {

    private static final String KEY_PREFIX = "lost-found/";
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov");

    private final MediaStorageClient mediaStorageClient;
    private final MediaUrlResolver mediaUrlResolver;
    private final ObjectStorageConfig storageConfig;

    // No @PostConstruct ensureBucketExists: MediaService already does it for this same bucket at
    // startup. Duplicating it doubles the HeadBucket call and breaks any test not stubbing it.

    public LostFoundMediaPresignResponse presignUpload(LostFoundMediaPresignRequest request, User currentUser) {
        String mediaKey = newKey(request.filename(), currentUser);
        String uploadUrl = mediaStorageClient.presignPutUrl(
                bucket(), mediaKey, request.contentType(), storageConfig.getPresignExpiryMinutes());
        return new LostFoundMediaPresignResponse(uploadUrl, mediaKey);
    }

    public LostFoundMediaUploadResponse uploadFile(MultipartFile file, User currentUser) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String mediaKey = newKey(originalFilename, currentUser);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            mediaStorageClient.putObject(bucket(), mediaKey, contentType, inputStream, file.getSize());
        } catch (Exception ex) {
            throw new MediaUploadException("Failed to upload file to object storage: " + ex.getMessage(), ex);
        }

        String mediaUrl = mediaUrlResolver.toViewableUrl(mediaKey);
        String mediaType = contentType.startsWith("video") ? "VIDEO" : "IMAGE";
        return new LostFoundMediaUploadResponse(mediaKey, mediaUrl, mediaType);
    }

    public void deleteMedia(String mediaKey, User currentUser) {
        assertOwnedKey(mediaKey, currentUser);
        mediaStorageClient.deleteObject(bucket(), mediaKey);
    }

    /**
     * Rejects any key that isn't under the caller's own lost-found prefix.
     *
     * <p>Called on <em>attach</em> as well as delete — the posting module only checks on delete,
     * which lets a client embed someone else's key in their own report. Not inheriting that.
     */
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
