package com.unisphere.backend.commerce.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param resumeKey    a key returned by {@code POST /jobs/applications/media/presign} or
 *                     {@code /upload}, owned by the caller — see {@code JobApplicationMediaService.assertOwnedKey}
 * @param coverLetter  optional
 */
public record CreateJobApplicationRequest(

        @NotBlank(message = "resumeKey is required")
        @Size(max = 500)
        String resumeKey,

        @Size(max = 5000, message = "Cover letter must not exceed 5000 characters")
        String coverLetter
) {}
