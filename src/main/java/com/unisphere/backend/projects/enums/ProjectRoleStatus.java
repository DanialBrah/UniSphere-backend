package com.unisphere.backend.projects.enums;

/**
 * Whether an open position is still accepting applications. Auto-managed by
 * {@code ProjectApplicationService}/{@code ProjectService} as {@code filledCount} crosses
 * {@code slots}, but also settable by the owner as a manual pause.
 */
public enum ProjectRoleStatus {
    OPEN,
    CLOSED
}
