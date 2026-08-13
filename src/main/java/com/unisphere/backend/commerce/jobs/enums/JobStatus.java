package com.unisphere.backend.commerce.jobs.enums;

/**
 * Job lifecycle. The transition table lives in {@code JobService.changeStatus}.
 */
public enum JobStatus {

    /** Being edited by its employer. Not visible on the public feed or search. */
    DRAFT,

    /** Live and accepting applications (if {@code applicationMode == INTERNAL}). */
    OPEN,

    /** Withdrawn by the employer, or auto-closed by {@code JobDeadlineScheduler} once the
     *  application deadline passes. Terminal. */
    CLOSED,

    /** The position has been filled. Terminal. */
    FILLED
}
