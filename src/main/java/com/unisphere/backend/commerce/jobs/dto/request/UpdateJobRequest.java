package com.unisphere.backend.commerce.jobs.dto.request;

import com.unisphere.backend.commerce.jobs.enums.ExperienceLevel;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationMode;
import com.unisphere.backend.commerce.jobs.enums.JobType;
import com.unisphere.backend.commerce.jobs.enums.WorkMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Patch semantics throughout: a null field leaves the current value untouched; a blank string
 * clears an optional text field. {@code applicationDeadline}/salary fields need an explicit clear
 * flag since {@code null} is ambiguous between "don't touch" and "clear" for non-string types —
 * same trick as {@code UpdateEventRequest.clearMaxCapacity}.
 */
public record UpdateJobRequest(

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Size(max = 5000, message = "Requirements must not exceed 5000 characters")
        String requirements,

        JobType jobType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,

        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @DecimalMin(value = "0.0", message = "salaryMin must not be negative")
        BigDecimal salaryMin,

        @DecimalMin(value = "0.0", message = "salaryMax must not be negative")
        BigDecimal salaryMax,

        Boolean clearSalary,

        @Pattern(regexp = "[A-Z]{3}", message = "salaryCurrency must be a 3-letter ISO 4217 code")
        String salaryCurrency,

        JobApplicationMode applicationMode,

        @Size(max = 500, message = "externalApplyUrl must not exceed 500 characters")
        String externalApplyUrl,

        @Future(message = "applicationDeadline must be in the future")
        LocalDate applicationDeadline,

        Boolean clearApplicationDeadline,

        Long universityId,

        Boolean clearUniversityId
) {}
