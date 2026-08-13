package com.unisphere.backend.projects.dto.response;

import com.unisphere.backend.projects.enums.ProjectRoleStatus;

public record ProjectRoleResponse(
        Long id,
        String title,
        String description,
        int slots,
        int filledCount,
        ProjectRoleStatus status
) {}
