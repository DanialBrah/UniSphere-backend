package com.unisphere.backend.commerce.jobs.dto.response;

import com.unisphere.backend.commerce.jobs.enums.ExperienceLevel;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationMode;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;
import com.unisphere.backend.commerce.jobs.enums.JobStatus;
import com.unisphere.backend.commerce.jobs.enums.JobType;
import com.unisphere.backend.commerce.jobs.enums.WorkMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full detail view of a job posting.
 *
 * @param viewerApplicationStatus the caller's own application on this job, or null if they haven't applied
 * @param canModify               whether the caller may edit, change status or delete this job
 */
public record JobResponse(
        Long id,
        JobEmployerResponse employer,
        String title,
        String description,
        String requirements,
        JobType jobType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        String location,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        JobApplicationMode applicationMode,
        String externalApplyUrl,
        LocalDate applicationDeadline,
        JobStatus status,
        int applicationCount,
        Long universityId,
        JobApplicationStatus viewerApplicationStatus,
        boolean canModify,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
