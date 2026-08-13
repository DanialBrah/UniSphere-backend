package com.unisphere.backend.commerce.jobs.entity;

import com.unisphere.backend.commerce.jobs.enums.ExperienceLevel;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationMode;
import com.unisphere.backend.commerce.jobs.enums.JobStatus;
import com.unisphere.backend.commerce.jobs.enums.JobType;
import com.unisphere.backend.commerce.jobs.enums.WorkMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A job posting. Posted only by {@code EMPLOYER}-role accounts (unlike {@code Event}, which allows
 * any organizer role) — see {@code JobAccessService.assertCanPost}.
 *
 * <p>Unlike {@code Event.universityId}, which is always derived from the organizer's own
 * affiliation, {@link #universityId} is set explicitly by the employer at creation time: an
 * {@code Employer} account has no university affiliation of its own. Null means visible to every
 * university.
 *
 * <p>{@code applicationCount} is a monotonic total (incremented on submit only, never decremented on
 * withdraw/reject/hire) — an informational "applications received" stat and the trigger for
 * {@code JobService.deleteJob}'s delete guard, not a capacity counter like
 * {@code Event.registeredCount}.
 */
@Entity
@Table(name = "jobs")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employer_id", nullable = false)
    private Long employerId;

    /** Null = visible to all universities. Set explicitly by the employer, never derived. */
    @Column(name = "university_id")
    private Long universityId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 10)
    private JobType jobType = JobType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 7)
    private WorkMode workMode = WorkMode.ON_SITE;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 12)
    private ExperienceLevel experienceLevel = ExperienceLevel.ENTRY_LEVEL;

    /** Required unless {@link #workMode} is {@code REMOTE}. */
    @Column(length = 255)
    private String location;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", nullable = false, length = 3)
    private String salaryCurrency = "MYR";

    @Enumerated(EnumType.STRING)
    @Column(name = "application_mode", nullable = false, length = 8)
    private JobApplicationMode applicationMode = JobApplicationMode.INTERNAL;

    /** Required iff {@link #applicationMode} is {@code EXTERNAL}. */
    @Column(name = "external_apply_url", length = 500)
    private String externalApplyUrl;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "application_count", nullable = false)
    private int applicationCount = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
