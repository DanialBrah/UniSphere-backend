package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.common.exception.MediaUploadException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.config.B2Config;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.posting.dto.request.MediaPresignRequest;
import com.unisphere.backend.social.posting.dto.response.MediaPresignResponse;
import com.unisphere.backend.social.posting.dto.response.MediaUploadResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.InputStream;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final B2Config b2Config;

    @PostConstruct
    void ensureBucketExists() {
        String bucket = b2Config.getBucket().getPosts();
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

    public MediaPresignResponse presignUpload(MediaPresignRequest request, User currentUser) {
        String ext = extractExtension(request.filename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + ext);
        }

        String bucket   = b2Config.getBucket().getPosts();
        String mediaKey = "posts/" + currentUser.getId() + "/" + UUID.randomUUID() + "." + ext;

        try {
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(b2Config.getPresignExpiryMinutes()))
                    .putObjectRequest(b -> b
                            .bucket(bucket)
                            .key(mediaKey)
                            .contentType(request.contentType())
                    )
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return new MediaPresignResponse(presigned.url().toString(), mediaKey);
        } catch (Exception ex) {
            throw new MediaUploadException("Could not generate presigned URL", ex);
        }
    }

    public MediaUploadResponse uploadFile(MultipartFile file, User currentUser) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + ext);
        }

        String bucket   = b2Config.getBucket().getPosts();
        String mediaKey = "posts/" + currentUser.getId() + "/" + UUID.randomUUID() + "." + ext;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    b -> b.bucket(bucket).key(mediaKey).contentType(contentType),
                    RequestBody.fromInputStream(inputStream, file.getSize())
            );
        } catch (Exception ex) {
            throw new MediaUploadException("Failed to upload file to B2: " + ex.getMessage(), ex);
        }

        String mediaUrl = b2Config.getEndpoint() + "/" + bucket + "/" + mediaKey;
        String mediaType = contentType.startsWith("video") ? "VIDEO" : "IMAGE";
        return new MediaUploadResponse(mediaKey, mediaUrl, mediaType);
    }

    public MediaPresignResponse presignAvatarUpload(MediaPresignRequest request, User currentUser) {
        String ext = extractExtension(request.filename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + ext);
        }

        String bucket   = b2Config.getBucket().getPosts();
        String mediaKey = "avatars/" + currentUser.getId() + "/" + UUID.randomUUID() + "." + ext;

        try {
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(b2Config.getPresignExpiryMinutes()))
                    .putObjectRequest(b -> b
                            .bucket(bucket)
                            .key(mediaKey)
                            .contentType(request.contentType())
                    )
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return new MediaPresignResponse(presigned.url().toString(), mediaKey);
        } catch (Exception ex) {
            throw new MediaUploadException("Could not generate presigned URL for avatar", ex);
        }
    }

    public void deleteMedia(String mediaKey, User currentUser) {
        Long userId = currentUser.getId();
        boolean owned = mediaKey.startsWith("posts/"   + userId + "/")
                     || mediaKey.startsWith("avatars/" + userId + "/");
        if (!owned) {
            throw new UnauthorizedActionException("Cannot delete media that does not belong to you");
        }

        String bucket = b2Config.getBucket().getPosts();
        try {
            s3Client.deleteObject(b -> b.bucket(bucket).key(mediaKey));
        } catch (Exception ex) {
            throw new MediaUploadException("Could not delete media: " + mediaKey, ex);
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }
}
