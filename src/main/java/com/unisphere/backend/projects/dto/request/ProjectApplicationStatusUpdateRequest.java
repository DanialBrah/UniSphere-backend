package com.unisphere.backend.projects.dto.request;

import com.unisphere.backend.projects.enums.ProjectApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Two disjoint actors funnel through this one request shape — see
 * {@code ProjectApplicationService.updateApplicationStatus}:
 * <ul>
 *   <li>{@code WITHDRAWN} — only the applicant themselves, from PENDING;</li>
 *   <li>{@code ACCEPTED}/{@code REJECTED} — only the project's owner or an admin.</li>
 * </ul>
 *
 * @param reason optional note — an owner's decision rationale, or the applicant's withdrawal reason.
 *              Always stored; surfaced to the other party in the resulting notification.
 */
public record ProjectApplicationStatusUpdateRequest(

        @NotNull(message = "status is required")
        ProjectApplicationStatus status,

        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason
) {}
