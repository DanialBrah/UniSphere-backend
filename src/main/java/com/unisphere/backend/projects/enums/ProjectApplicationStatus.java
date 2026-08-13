package com.unisphere.backend.projects.enums;

/**
 * Application review pipeline. {@code ACCEPTED}/{@code REJECTED}/{@code WITHDRAWN} are terminal.
 * Only the applicant may set {@code WITHDRAWN}; only the project's owner/an admin may set
 * {@code ACCEPTED} or {@code REJECTED} — see {@code ProjectApplicationService.updateApplicationStatus}.
 */
public enum ProjectApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
