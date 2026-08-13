package com.unisphere.backend.projects.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.projects.dto.request.ProjectMediaPresignRequest;
import com.unisphere.backend.projects.dto.response.ProjectMediaPresignResponse;
import com.unisphere.backend.projects.dto.response.ProjectMediaUploadResponse;
import com.unisphere.backend.projects.service.ProjectMediaService;
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

@Tag(name = "Project media", description = "Upload plumbing for a project's cover image")
@RestController
@RequestMapping("/api/v1/projects/media")
@RequiredArgsConstructor
public class ProjectMediaController {

    private final ProjectMediaService projectMediaService;

    @Operation(summary = "Get a presigned URL for a direct browser-to-storage cover image upload")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<ProjectMediaPresignResponse>> presign(
            @Valid @RequestBody ProjectMediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectMediaService.presignUpload(req, currentUser)));
    }

    @Operation(summary = "Upload a cover image through the API")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectMediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                projectMediaService.uploadFile(file, currentUser), "File uploaded"));
    }

    @Operation(summary = "Delete an uploaded cover image you own")
    @DeleteMapping("/{*mediaKey}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String mediaKey,
            @AuthenticationPrincipal User currentUser) {
        // PathPatternParser captures the rest-of-path with a leading "/", strip it
        String key = mediaKey.startsWith("/") ? mediaKey.substring(1) : mediaKey;
        projectMediaService.deleteMedia(key, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Media deleted"));
    }
}
