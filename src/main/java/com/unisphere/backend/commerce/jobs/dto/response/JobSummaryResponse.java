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

/** List-view shape — trims {@code description}/{@code requirements}, same as {@code EventSummaryResponse}. */
public record JobSummaryResponse(
        Long id,
        JobEmployerResponse employer,
        String title,
        JobType jobType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        String location,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        JobApplicationMode applicationMode,
        JobStatus status,
        LocalDate applicationDeadline,
        Long universityId,
        JobApplicationStatus viewerApplicationStatus,
        boolean canModify,
        LocalDateTime createdAt
) {}
