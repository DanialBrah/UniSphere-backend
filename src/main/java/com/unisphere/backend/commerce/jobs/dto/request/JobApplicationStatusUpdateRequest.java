package com.unisphere.backend.commerce.jobs.dto.request;

import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Two disjoint actors funnel through this one request shape — see
 * {@code JobApplicationService.updateApplicationStatus}:
 * <ul>
 *   <li>{@code WITHDRAWN} — only the applicant themselves, from SUBMITTED/REVIEWED/SHORTLISTED;</li>
 *   <li>anything else (REVIEWED/SHORTLISTED/REJECTED/HIRED) — only the job's employer or an admin.</li>
 * </ul>
 *
 * @param reason optional note — an employer's decision rationale, or the applicant's withdrawal reason.
 *              Always stored; surfaced to the other party in the resulting notification.
 */
public record JobApplicationStatusUpdateRequest(

        @NotNull(message = "status is required")
        JobApplicationStatus status,

        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason
) {}
