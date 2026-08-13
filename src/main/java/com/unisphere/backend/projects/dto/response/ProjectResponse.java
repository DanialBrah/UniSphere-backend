package com.unisphere.backend.projects.dto.response;

import com.unisphere.backend.projects.enums.ProjectStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full detail view of a project. Roles are embedded inline (small, bounded); members are fetched
 * separately via {@code GET /projects/{id}/members}, which is paginated since a team can grow.
 *
 * @param canModify whether the caller may edit, change status, delete this project, or manage its
 *                  roles/applications
 */
public record ProjectResponse(
        Long id,
        ProjectActorResponse owner,
        String title,
        String description,
        String coverImageUrl,
        String githubUrl,
        String demoUrl,
        ProjectStatus status,
        boolean isRecruiting,
        Long universityId,
        List<ProjectRoleResponse> roles,
        int memberCount,
        boolean canModify,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
