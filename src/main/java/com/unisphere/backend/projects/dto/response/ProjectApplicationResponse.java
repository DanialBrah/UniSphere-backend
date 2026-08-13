package com.unisphere.backend.projects.dto.response;

import com.unisphere.backend.projects.enums.ProjectApplicationStatus;

import java.time.LocalDateTime;

public record ProjectApplicationResponse(
        Long id,
        Long projectId,
        String projectTitle,
        Long projectRoleId,
        String roleTitle,
        Long applicantId,
        ProjectActorResponse applicant,
        String message,
        ProjectApplicationStatus status,
        String decisionReason,
        LocalDateTime reviewedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt
) {}
