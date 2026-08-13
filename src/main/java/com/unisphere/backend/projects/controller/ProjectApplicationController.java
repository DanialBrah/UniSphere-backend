package com.unisphere.backend.projects.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.projects.dto.request.CreateProjectApplicationRequest;
import com.unisphere.backend.projects.dto.request.ProjectApplicationStatusUpdateRequest;
import com.unisphere.backend.projects.dto.response.ProjectApplicationResponse;
import com.unisphere.backend.projects.enums.ProjectApplicationStatus;
import com.unisphere.backend.projects.service.ProjectApplicationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project applications", description = "Apply to join a project's open role, and review/decide/withdraw applications")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectApplicationController {

    private final ProjectApplicationService projectApplicationService;

    @Operation(summary = "Apply to join a specific open role — STUDENT/ALUMNI only")
    @PostMapping("/{projectId}/roles/{roleId}/applications")
    public ResponseEntity<ApiResponse<ProjectApplicationResponse>> apply(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @Valid @RequestBody CreateProjectApplicationRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(projectApplicationService.apply(projectId, roleId, req, currentUser), "Application submitted"));
    }

    @Operation(summary = "Applicant roster across every role on a project you own")
    @GetMapping("/{projectId}/applications")
    public ResponseEntity<ApiResponse<Page<ProjectApplicationResponse>>> listApplications(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) ProjectApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                projectApplicationService.listApplications(projectId, roleId, status, pageable, currentUser)));
    }

    @Operation(summary = "The caller's own applications across every project")
    @GetMapping("/applications/me")
    public ResponseEntity<ApiResponse<Page<ProjectApplicationResponse>>> myApplications(
            @RequestParam(required = false) ProjectApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectApplicationService.myApplications(status, pageable, currentUser)));
    }

    @Operation(summary = "Owner accepts/rejects, or the applicant withdraws, their own application")
    @PatchMapping("/applications/{applicationId}")
    public ResponseEntity<ApiResponse<ProjectApplicationResponse>> updateApplicationStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody ProjectApplicationStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                projectApplicationService.updateApplicationStatus(applicationId, req, currentUser), "Application updated"));
    }
}
