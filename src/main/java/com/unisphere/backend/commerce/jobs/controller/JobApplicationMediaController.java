package com.unisphere.backend.commerce.jobs.controller;

import com.unisphere.backend.commerce.jobs.dto.request.JobApplicationMediaPresignRequest;
import com.unisphere.backend.commerce.jobs.dto.response.JobApplicationMediaPresignResponse;
import com.unisphere.backend.commerce.jobs.dto.response.JobApplicationMediaUploadResponse;
import com.unisphere.backend.commerce.jobs.service.JobApplicationMediaService;
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

@Tag(name = "Job application media", description = "Upload plumbing for a job application's résumé/CV")
@RestController
@RequestMapping("/api/v1/jobs/applications/media")
@RequiredArgsConstructor
public class JobApplicationMediaController {

    private final JobApplicationMediaService jobApplicationMediaService;

    @Operation(summary = "Get a presigned URL for a direct browser-to-storage résumé upload")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<JobApplicationMediaPresignResponse>> presign(
            @Valid @RequestBody JobApplicationMediaPresignRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobApplicationMediaService.presignUpload(req, currentUser)));
    }

    @Operation(summary = "Upload a résumé/CV through the API")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<JobApplicationMediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplicationMediaService.uploadFile(file, currentUser), "File uploaded"));
    }

    @Operation(summary = "Delete an uploaded résumé you own")
    @DeleteMapping("/{*mediaKey}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String mediaKey,
            @AuthenticationPrincipal User currentUser) {
        // PathPatternParser captures the rest-of-path with a leading "/", strip it
        String key = mediaKey.startsWith("/") ? mediaKey.substring(1) : mediaKey;
        jobApplicationMediaService.deleteMedia(key, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Media deleted"));
    }
}
