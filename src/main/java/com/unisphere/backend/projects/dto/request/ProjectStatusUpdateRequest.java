package com.unisphere.backend.projects.dto.request;

import com.unisphere.backend.projects.enums.ProjectStatus;
import jakarta.validation.constraints.NotNull;

public record ProjectStatusUpdateRequest(

        @NotNull(message = "status is required")
        ProjectStatus status
) {}
