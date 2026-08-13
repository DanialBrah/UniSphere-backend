package com.unisphere.backend.projects.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.projects.dto.request.CreateProjectRequest;
import com.unisphere.backend.projects.dto.request.CreateProjectRoleRequest;
import com.unisphere.backend.projects.dto.request.ProjectStatusUpdateRequest;
import com.unisphere.backend.projects.dto.request.UpdateProjectRequest;
import com.unisphere.backend.projects.dto.request.UpdateProjectRoleRequest;
import com.unisphere.backend.projects.dto.response.ProjectResponse;
import com.unisphere.backend.projects.dto.response.ProjectRoleResponse;
import com.unisphere.backend.projects.dto.response.ProjectSummaryResponse;
import com.unisphere.backend.projects.enums.ProjectStatus;
import com.unisphere.backend.projects.service.ProjectService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Projects", description = "Showcase, browse and manage projects — student/alumni/club-owned, with structured open roles")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Showcase a new project — STUDENT/ALUMNI/CLUB only, always starts OPEN")
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(projectService.createProject(req, currentUser), "Project created"));
    }

    @Operation(summary = "Browse projects, filtered by status, recruiting, university and owner")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProjectSummaryResponse>>> getFeed(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) Boolean recruiting,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) Long ownerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                projectService.getFeed(status, recruiting, universityId, ownerId, pageable, currentUser)));
    }

    @Operation(summary = "Full-text search over project titles and descriptions")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProjectSummaryResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.search(q, pageable, currentUser)));
    }

    @Operation(summary = "The caller's own projects")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<ProjectSummaryResponse>>> getMyProjects(
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyProjects(status, pageable, currentUser)));
    }

    @Operation(summary = "Projects the caller has joined as a contributor (not owner)")
    @GetMapping("/joined")
    public ResponseEntity<ApiResponse<Page<ProjectSummaryResponse>>> getJoinedProjects(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getJoinedProjects(pageable, currentUser)));
    }

    @Operation(summary = "Get one project, including its open roles")
    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProjectById(projectId, currentUser)));
    }

    @Operation(summary = "Update a project you own")
    @PutMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.updateProject(projectId, req, currentUser), "Project updated"));
    }

    @Operation(summary = "Move a project through OPEN/IN_PROGRESS/COMPLETED — COMPLETED is terminal")
    @PatchMapping("/{projectId}/status")
    public ResponseEntity<ApiResponse<ProjectResponse>> changeStatus(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.changeStatus(projectId, req, currentUser), "Project status updated"));
    }

    @Operation(summary = "Delete a project you own — blocked once it has other members")
    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User currentUser) {
        projectService.deleteProject(projectId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Project deleted"));
    }

    // ── Roles ────────────────────────────────────────────────────────────────

    @Operation(summary = "Add an open role a project is recruiting for")
    @PostMapping("/{projectId}/roles")
    public ResponseEntity<ApiResponse<ProjectRoleResponse>> addRole(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateProjectRoleRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(projectService.addRole(projectId, req, currentUser), "Role added"));
    }

    @Operation(summary = "List a project's open roles")
    @GetMapping("/{projectId}/roles")
    public ResponseEntity<ApiResponse<List<ProjectRoleResponse>>> listRoles(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.listRoles(projectId, currentUser)));
    }

    @Operation(summary = "Update a role's title, description, slots or status")
    @PutMapping("/{projectId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<ProjectRoleResponse>> updateRole(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateProjectRoleRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.updateRole(projectId, roleId, req, currentUser), "Role updated"));
    }

    @Operation(summary = "Remove a role — blocked once it has received applications; close it instead")
    @DeleteMapping("/{projectId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @AuthenticationPrincipal User currentUser) {
        projectService.deleteRole(projectId, roleId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role removed"));
    }
}
