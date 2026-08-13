package com.unisphere.backend.commerce.jobs.enums;

/**
 * {@code INTERNAL} — apply in-app ("Easy Apply"): resume + cover letter, handled by
 * {@code JobApplicationService}. {@code EXTERNAL} — the job carries an {@code externalApplyUrl} to
 * the employer's own site instead; {@code JobApplicationService.apply} rejects any in-app attempt on
 * such a job. Mirrors {@code EventRegistrationMode} exactly.
 */
public enum JobApplicationMode {
    INTERNAL,
    EXTERNAL
}
