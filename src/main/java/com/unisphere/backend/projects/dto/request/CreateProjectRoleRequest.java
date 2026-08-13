package com.unisphere.backend.projects.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param slots null means 1 */
public record CreateProjectRoleRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title must not exceed 100 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Min(value = 1, message = "slots must be at least 1")
        Integer slots
) {}
