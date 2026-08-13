package com.unisphere.backend.commerce.jobs.dto.request;

import com.unisphere.backend.commerce.jobs.enums.ExperienceLevel;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationMode;
import com.unisphere.backend.commerce.jobs.enums.JobType;
import com.unisphere.backend.commerce.jobs.enums.WorkMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A new job posting. Always created {@code DRAFT} — publishing is an explicit later action via
 * {@code PATCH /jobs/{id}/status}.
 *
 * <p>Unlike {@code CreateEventRequest}, {@code universityId} IS a request field: an
 * {@code EMPLOYER} account has no university affiliation to derive it from. Null means visible to
 * every university. It is validated server-side to actually reference a {@code University} account.
 *
 * <p>Four cross-field rules cannot be expressed with bean validation and are enforced in
 * {@code JobService}, surfacing as 400 {@code BAD_REQUEST}:
 * <ol>
 *   <li>{@code workMode = REMOTE} forbids {@code location}; any other work mode requires it;</li>
 *   <li>{@code applicationMode = EXTERNAL} requires {@code externalApplyUrl}; {@code INTERNAL} forbids it;</li>
 *   <li>{@code salaryMin} must not exceed {@code salaryMax} when both are set;</li>
 *   <li>{@code universityId}, if set, must resolve to a {@code University} account.</li>
 * </ol>
 */
public record CreateJobRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Size(max = 5000, message = "Requirements must not exceed 5000 characters")
        String requirements,

        /* Null means FULL_TIME — matches CreateEventRequest.category's null-default pattern. */
        JobType jobType,

        /* Null means ON_SITE. */
        WorkMode workMode,

        /* Null means ENTRY_LEVEL. */
        ExperienceLevel experienceLevel,

        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @DecimalMin(value = "0.0", message = "salaryMin must not be negative")
        BigDecimal salaryMin,

        @DecimalMin(value = "0.0", message = "salaryMax must not be negative")
        BigDecimal salaryMax,

        /* Null defaults to jobs.default-salary-currency server-side. */
        @Pattern(regexp = "[A-Z]{3}", message = "salaryCurrency must be a 3-letter ISO 4217 code")
        String salaryCurrency,

        /* Null means INTERNAL. */
        JobApplicationMode applicationMode,

        @Size(max = 500, message = "externalApplyUrl must not exceed 500 characters")
        String externalApplyUrl,

        @Future(message = "applicationDeadline must be in the future")
        LocalDate applicationDeadline,

        /* Null means visible to every university. */
        Long universityId
) {}
