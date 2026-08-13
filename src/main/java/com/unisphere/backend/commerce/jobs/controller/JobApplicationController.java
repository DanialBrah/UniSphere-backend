package com.unisphere.backend.commerce.jobs.controller;

import com.unisphere.backend.commerce.jobs.dto.request.CreateJobApplicationRequest;
import com.unisphere.backend.commerce.jobs.dto.request.JobApplicationStatusUpdateRequest;
import com.unisphere.backend.commerce.jobs.dto.response.JobApplicationResponse;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;
import com.unisphere.backend.commerce.jobs.service.JobApplicationService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /applications} (apply) and the job-scoped roster nest under {@code /jobs/{id}}; "my
 * applications" and single-application status changes are flat, since an application id is
 * globally unique — the exact split {@code EventRegistrationController} draws.
 */
@Tag(name = "Job applications", description = "Apply for jobs (Easy Apply), manage your applications, and employer review")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @Operation(summary = "Apply to a job (Easy Apply) — STUDENT/ALUMNI only, INTERNAL application mode only")
    @PostMapping("/{jobId}/applications")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> apply(
            @PathVariable Long jobId,
            @Valid @RequestBody CreateJobApplicationRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(jobApplicationService.apply(jobId, req, currentUser), "Application submitted"));
    }

    @Operation(summary = "Applicant roster for a job you posted")
    @GetMapping("/{jobId}/applications")
    public ResponseEntity<ApiResponse<Page<JobApplicationResponse>>> listApplications(
            @PathVariable Long jobId,
            @RequestParam(required = false) JobApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplicationService.listApplications(jobId, status, pageable, currentUser)));
    }

    @Operation(summary = "Your own application for one job")
    @GetMapping("/{jobId}/applications/me")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> myApplicationForJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobApplicationService.myApplicationForJob(jobId, currentUser)));
    }

    @Operation(summary = "\"My applications\" — every application you've submitted, across every job")
    @GetMapping("/applications/me")
    public ResponseEntity<ApiResponse<Page<JobApplicationResponse>>> myApplications(
            @RequestParam(required = false) JobApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobApplicationService.myApplications(status, pageable, currentUser)));
    }

    @Operation(summary = "Update an application's status — employer review decision, or applicant self-withdraw")
    @PatchMapping("/applications/{applicationId}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> updateApplicationStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody JobApplicationStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplicationService.updateApplicationStatus(applicationId, req, currentUser),
                "Application status updated"));
    }
}
