package com.unisphere.backend.commerce.jobs.dto.response;

import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;

import java.time.LocalDateTime;

public record JobApplicationResponse(
        Long id,
        Long jobId,
        String jobTitle,
        Long applicantId,
        JobApplicantResponse applicant,
        String resumeUrl,
        String coverLetter,
        JobApplicationStatus status,
        String decisionReason,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt
) {}
