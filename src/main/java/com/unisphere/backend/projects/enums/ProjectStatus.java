package com.unisphere.backend.projects.enums;

/**
 * Project lifecycle. The transition table lives in {@code ProjectService.changeStatus}.
 */
public enum ProjectStatus {

    /** Live and showcased. Recruiting, if {@code isRecruiting} is set, happens in this state. */
    OPEN,

    /** Actively being built. Still visible; recruiting is still possible. */
    IN_PROGRESS,

    /** The project has wrapped up. Terminal — no transition out. */
    COMPLETED
}
