package com.unisphere.backend.commerce.jobs.dto.request;

import com.unisphere.backend.commerce.jobs.enums.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusUpdateRequest(

        @NotNull(message = "status is required")
        JobStatus status
) {}
