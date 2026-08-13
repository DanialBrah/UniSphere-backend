package com.unisphere.backend.projects.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.projects.dto.response.ProjectMemberResponse;
import com.unisphere.backend.projects.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project members", description = "A project's team roster — leave or remove a member")
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectService projectService;

    @Operation(summary = "List a project's team roster")
    @GetMapping("/members")
    public ResponseEntity<ApiResponse<Page<ProjectMemberResponse>>> listMembers(
            @PathVariable Long projectId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.listMembers(projectId, pageable, currentUser)));
    }

    @Operation(summary = "Leave a project you've joined — blocked for the owner")
    @DeleteMapping("/members/me")
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User currentUser) {
        projectService.leaveProject(projectId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Left project"));
    }

    @Operation(summary = "Remove a member — owner/admin only, never the owner's own row")
    @DeleteMapping("/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        projectService.removeMember(projectId, userId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Member removed"));
    }
}
