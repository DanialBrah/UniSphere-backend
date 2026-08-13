package com.unisphere.backend.projects.dto.request;

import com.unisphere.backend.projects.enums.ProjectRoleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Patch semantics: a null field leaves the current value untouched — same convention as {@code UpdateJobRequest}. */
public record UpdateProjectRoleRequest(

        @Size(max = 100, message = "Title must not exceed 100 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Min(value = 1, message = "slots must be at least 1")
        Integer slots,

        ProjectRoleStatus status
) {}
