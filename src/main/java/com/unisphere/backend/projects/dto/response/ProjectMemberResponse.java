package com.unisphere.backend.projects.dto.response;

import com.unisphere.backend.projects.enums.ProjectMemberRole;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        Long userId,
        String displayName,
        String avatarUrl,
        ProjectMemberRole role,
        Long projectRoleId,
        String roleTitle,
        LocalDateTime joinedAt
) {}
