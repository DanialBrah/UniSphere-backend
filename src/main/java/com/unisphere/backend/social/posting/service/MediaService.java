package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.common.exception.MediaUploadException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.config.ObjectStorageConfig;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.posting.dto.request.MediaPresignRequest;
import com.unisphere.backend.social.posting.dto.response.MediaPresignResponse;
import com.unisphere.backend.social.posting.dto.response.MediaUploadResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov");

    private final MediaStorageClient mediaStorageClient;
    private final ObjectStorageConfig storageConfig;

    @PostConstruct
    void ensureBucketExists() {
        mediaStorageClient.ensureBucketExists(storageConfig.getBucket().getPosts());
    }

    public MediaPresignResponse presignUpload(MediaPresignRequest request, User currentUser) {
        String ext = extractExtension(request.filename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + ext);
        }

        String bucket   = storageConfig.getBucket().getPosts();
        String mediaKey = "posts/" + currentUser.getId() + "/" + UUID.randomUUID() + "." + ext;

        String uploadUrl = mediaStorageClient.presignPutUrl(
                bucket, mediaKey, request.contentType(), storageConfig.getPresignExpiryMinutes());
        return new MediaPresignResponse(uploadUrl, mediaKey);
    }

    public MediaUploadResponse uploadFile(MultipartFile file, User currentUser) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + ext);
        }

        String bucket   = storageConfig.getBucket().getPosts();
        String mediaKey = "posts/" + currentUser.getId() + "/" + UUID.randomUUID() + "." + ext;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            mediaStorageClient.putObject(bucket, mediaKey, contentType, inputStream, file.getSize());
        } catch (Exception ex) {
            throw new MediaUploadException("Failed to upload file to object storage: " + ex.getMessage(), ex);
        }

        String mediaUrl = storageConfig.resolveMediaUrl(mediaKey);
        String mediaType = contentType.startsWith("video") ? "VIDEO" : "IMAGE";
        return new MediaUploadResponse(mediaKey, mediaUrl, mediaType);
    }

    public MediaPresignResponse presignAvatarUpload(MediaPresignRequest request, User currentUser) {
        String ext = extractExtension(request.filename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + ext);
        }

        String bucket   = storageConfig.getBucket().getPosts();
        String mediaKey = "avatars/" + currentUser.getId() + "/" + UUID.randomUUID() + "." + ext;

        String uploadUrl = mediaStorageClient.presignPutUrl(
                bucket, mediaKey, request.contentType(), storageConfig.getPresignExpiryMinutes());
        return new MediaPresignResponse(uploadUrl, mediaKey);
    }

    public void deleteMedia(String mediaKey, User currentUser) {
        Long userId = currentUser.getId();
        boolean owned = mediaKey.startsWith("posts/"   + userId + "/")
                     || mediaKey.startsWith("avatars/" + userId + "/");
        if (!owned) {
            throw new UnauthorizedActionException("Cannot delete media that does not belong to you");
        }

        mediaStorageClient.deleteObject(storageConfig.getBucket().getPosts(), mediaKey);
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }
}
