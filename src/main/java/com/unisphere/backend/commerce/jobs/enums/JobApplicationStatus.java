package com.unisphere.backend.commerce.jobs.enums;

/**
 * Application review pipeline. {@code REJECTED}/{@code HIRED}/{@code WITHDRAWN} are terminal.
 * Only the applicant may set {@code WITHDRAWN}; only the job's employer/an admin may set any other
 * non-{@code SUBMITTED} value — see {@code JobApplicationService.updateApplicationStatus}.
 */
public enum JobApplicationStatus {
    SUBMITTED,
    REVIEWED,
    SHORTLISTED,
    REJECTED,
    HIRED,
    WITHDRAWN
}
