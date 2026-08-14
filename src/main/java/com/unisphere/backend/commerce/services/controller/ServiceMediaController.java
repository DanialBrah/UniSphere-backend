package com.unisphere.backend.commerce.services.controller;

import com.unisphere.backend.commerce.services.dto.request.ServiceMediaPresignRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceMediaPresignResponse;
import com.unisphere.backend.commerce.services.dto.response.ServiceMediaUploadResponse;
import com.unisphere.backend.commerce.services.service.ServiceMediaService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Service media", description = "Upload plumbing for a service listing's portfolio image")
@RestController
@RequestMapping("/api/v1/services/media")
@RequiredArgsConstructor
public class ServiceMediaController {

    private final ServiceMediaService serviceMediaService;

    @Operation(summary = "Get a presigned URL for a direct browser-to-storage portfolio image upload")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<ServiceMediaPresignResponse>> presign(
            @Valid @RequestBody ServiceMediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceMediaService.presignUpload(req, currentUser)));
    }

    @Operation(summary = "Upload a portfolio image through the API")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ServiceMediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                serviceMediaService.uploadFile(file, currentUser), "File uploaded"));
    }

    @Operation(summary = "Delete an uploaded portfolio image you own")
    @DeleteMapping("/{*mediaKey}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String mediaKey,
            @AuthenticationPrincipal User currentUser) {
        // PathPatternParser captures the rest-of-path with a leading "/", strip it
        String key = mediaKey.startsWith("/") ? mediaKey.substring(1) : mediaKey;
        serviceMediaService.deleteMedia(key, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Media deleted"));
    }
}
