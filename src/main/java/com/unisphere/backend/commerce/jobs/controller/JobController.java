package com.unisphere.backend.commerce.jobs.controller;

import com.unisphere.backend.commerce.jobs.dto.request.CreateJobRequest;
import com.unisphere.backend.commerce.jobs.dto.request.JobStatusUpdateRequest;
import com.unisphere.backend.commerce.jobs.dto.request.UpdateJobRequest;
import com.unisphere.backend.commerce.jobs.dto.response.JobResponse;
import com.unisphere.backend.commerce.jobs.dto.response.JobStatsResponse;
import com.unisphere.backend.commerce.jobs.dto.response.JobSummaryResponse;
import com.unisphere.backend.commerce.jobs.enums.ExperienceLevel;
import com.unisphere.backend.commerce.jobs.enums.JobStatus;
import com.unisphere.backend.commerce.jobs.enums.JobType;
import com.unisphere.backend.commerce.jobs.enums.WorkMode;
import com.unisphere.backend.commerce.jobs.service.JobService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Jobs", description = "Create, browse and manage job postings — employer-only posting, student/alumni applications")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(summary = "Post a job — EMPLOYER only, always starts as DRAFT")
    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Valid @RequestBody CreateJobRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(jobService.createJob(req, currentUser), "Job posted"));
    }

    /**
     * Browse. A null {@code status} defaults to OPEN — DRAFT is rejected outright, see {@code JobService}.
     */
    @Operation(summary = "Browse jobs, filtered by type, work mode, experience level, university and status")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobSummaryResponse>>> getFeed(
            @RequestParam(required = false) JobType jobType,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) ExperienceLevel experienceLevel,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobService.getFeed(jobType, workMode, experienceLevel, universityId, status, pageable, currentUser)));
    }

    @Operation(summary = "Full-text search over job titles and descriptions")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<JobSummaryResponse>>> search(
            @RequestParam String q,
            @RequestParam(required = false) JobType jobType,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.search(q, jobType, pageable, currentUser)));
    }

    @Operation(summary = "The caller's own job postings, every status including DRAFT")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<JobSummaryResponse>>> getMyJobs(
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getMyJobs(status, pageable, currentUser)));
    }

    @Operation(summary = "Get one job. A DRAFT is masked as 404 unless you're the employer/ADMIN")
    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJobById(jobId, currentUser)));
    }

    @Operation(summary = "Update a job you posted")
    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable Long jobId,
            @Valid @RequestBody UpdateJobRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.updateJob(jobId, req, currentUser), "Job updated"));
    }

    @Operation(summary = "Publish, close or fill a job")
    @PatchMapping("/{jobId}/status")
    public ResponseEntity<ApiResponse<JobResponse>> changeStatus(
            @PathVariable Long jobId,
            @Valid @RequestBody JobStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.changeStatus(jobId, req, currentUser), "Job status updated"));
    }

    @Operation(summary = "Delete a job you posted — blocked once it has received applications")
    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        jobService.deleteJob(jobId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Job deleted"));
    }

    @Operation(summary = "Applicant status breakdown for a job you posted")
    @GetMapping("/{jobId}/stats")
    public ResponseEntity<ApiResponse<JobStatsResponse>> getStats(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getStats(jobId, currentUser)));
    }
}
