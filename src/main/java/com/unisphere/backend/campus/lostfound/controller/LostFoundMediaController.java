package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.campus.lostfound.dto.request.LostFoundMediaPresignRequest;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundMediaPresignResponse;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundMediaUploadResponse;
import com.unisphere.backend.campus.lostfound.service.LostFoundMediaService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Lost & Found media", description = "Upload plumbing for item photos and claim proof images")
@RestController
@RequestMapping("/api/v1/lost-found/media")
@RequiredArgsConstructor
public class LostFoundMediaController {

    private final LostFoundMediaService lostFoundMediaService;

    @Operation(summary = "Get a presigned URL for a direct browser-to-storage upload")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<LostFoundMediaPresignResponse>> presign(
            @Valid @RequestBody LostFoundMediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(lostFoundMediaService.presignUpload(req, currentUser)));
    }

    @Operation(summary = "Upload a file through the API")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LostFoundMediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                ApiResponse.ok(lostFoundMediaService.uploadFile(file, currentUser), "File uploaded"));
    }

    @Operation(summary = "Delete an uploaded file you own")
    @DeleteMapping("/{*mediaKey}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String mediaKey,
            @AuthenticationPrincipal User currentUser) {
        // PathPatternParser captures the rest-of-path with a leading "/", strip it
        String key = mediaKey.startsWith("/") ? mediaKey.substring(1) : mediaKey;
        lostFoundMediaService.deleteMedia(key, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Media deleted"));
    }
}
