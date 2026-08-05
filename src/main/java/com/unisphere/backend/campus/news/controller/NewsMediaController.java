package com.unisphere.backend.campus.news.controller;

import com.unisphere.backend.campus.news.dto.request.NewsMediaPresignRequest;
import com.unisphere.backend.campus.news.dto.response.NewsMediaPresignResponse;
import com.unisphere.backend.campus.news.dto.response.NewsMediaUploadResponse;
import com.unisphere.backend.campus.news.service.NewsMediaService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/news/media")
@RequiredArgsConstructor
public class NewsMediaController {

    private final NewsMediaService newsMediaService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<NewsMediaPresignResponse>> presign(
            @Valid @RequestBody NewsMediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsMediaService.presignUpload(req, currentUser)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsMediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsMediaService.uploadFile(file, currentUser), "File uploaded"));
    }

    @DeleteMapping("/{*mediaKey}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String mediaKey,
            @AuthenticationPrincipal User currentUser) {
        // PathPatternParser captures the rest-of-path with a leading "/", strip it
        String key = mediaKey.startsWith("/") ? mediaKey.substring(1) : mediaKey;
        newsMediaService.deleteMedia(key, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Media deleted"));
    }
}
