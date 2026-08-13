package com.unisphere.backend.commerce.jobs.dto.response;

/** Applicant-status breakdown for one job. Backs the employer's dashboard view. */
public record JobStatsResponse(
        Long submitted,
        Long reviewed,
        Long shortlisted,
        Long rejected,
        Long hired,
        Long withdrawn,
        int totalApplications
) {}
