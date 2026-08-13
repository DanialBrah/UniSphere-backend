package com.unisphere.backend.projects.dto.response;

import com.unisphere.backend.projects.enums.ProjectStatus;

import java.time.LocalDateTime;

/** List-view shape — trims {@code description}/{@code roles}, same as {@code JobSummaryResponse}. */
public record ProjectSummaryResponse(
        Long id,
        ProjectActorResponse owner,
        String title,
        String coverImageUrl,
        ProjectStatus status,
        boolean isRecruiting,
        Long universityId,
        int memberCount,
        int openRolesCount,
        boolean canModify,
        LocalDateTime createdAt
) {}
