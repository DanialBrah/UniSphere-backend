package com.unisphere.backend.commerce.jobs.enums;

/**
 * {@code REMOTE} requires no {@code location}; {@code ON_SITE}/{@code HYBRID} require one — enforced
 * in {@code JobService}, mirroring {@code Event}'s online/venue rule.
 */
public enum WorkMode {
    ON_SITE,
    REMOTE,
    HYBRID
}
