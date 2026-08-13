package com.unisphere.backend.commerce.events.controller;

import com.unisphere.backend.commerce.events.dto.request.EventMediaPresignRequest;
import com.unisphere.backend.commerce.events.dto.response.EventMediaPresignResponse;
import com.unisphere.backend.commerce.events.dto.response.EventMediaUploadResponse;
import com.unisphere.backend.commerce.events.service.EventMediaService;
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

@Tag(name = "Event media", description = "Upload plumbing for event cover images")
@RestController
@RequestMapping("/api/v1/events/media")
@RequiredArgsConstructor
public class EventMediaController {

    private final EventMediaService eventMediaService;

    @Operation(summary = "Get a presigned URL for a direct browser-to-storage upload")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<EventMediaPresignResponse>> presign(
            @Valid @RequestBody EventMediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventMediaService.presignUpload(req, currentUser)));
    }

    @Operation(summary = "Upload a cover image through the API")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<EventMediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventMediaService.uploadFile(file, currentUser), "File uploaded"));
    }

    @Operation(summary = "Delete an uploaded file you own")
    @DeleteMapping("/{*mediaKey}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String mediaKey,
            @AuthenticationPrincipal User currentUser) {
        // PathPatternParser captures the rest-of-path with a leading "/", strip it
        String key = mediaKey.startsWith("/") ? mediaKey.substring(1) : mediaKey;
        eventMediaService.deleteMedia(key, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Media deleted"));
    }
}
