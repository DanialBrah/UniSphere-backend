package com.unisphere.backend.social.posting.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.posting.dto.request.MediaPresignRequest;
import com.unisphere.backend.social.posting.dto.response.MediaPresignResponse;
import com.unisphere.backend.social.posting.dto.response.MediaUploadResponse;
import com.unisphere.backend.social.posting.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<MediaPresignResponse>> presign(
            @Valid @RequestBody MediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(mediaService.presignUpload(req, currentUser)));
    }

    @PostMapping("/presign-avatar")
    public ResponseEntity<ApiResponse<MediaPresignResponse>> presignAvatar(
            @Valid @RequestBody MediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(mediaService.presignAvatarUpload(req, currentUser)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(mediaService.uploadFile(file, currentUser), "File uploaded"));
    }

    @DeleteMapping("/{*mediaKey}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String mediaKey,
            @AuthenticationPrincipal User currentUser) {
        // PathPatternParser captures the rest-of-path with a leading "/", strip it
        String key = mediaKey.startsWith("/") ? mediaKey.substring(1) : mediaKey;
        mediaService.deleteMedia(key, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Media deleted"));
    }
}
