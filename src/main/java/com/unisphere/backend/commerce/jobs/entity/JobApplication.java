package com.unisphere.backend.commerce.jobs.entity;

import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * One applicant's application to one job. Audit history — never soft-deleted, so this entity
 * carries no {@code @SQLRestriction}; self-withdrawal is {@code status = WITHDRAWN}, matching
 * {@code EventRegistration}.
 *
 * <p>Unlike {@code EventRegistration}, {@code job_applications} carries a hard
 * {@code UNIQUE(job_id, applicant_id)} constraint (see migration {@code 019}) — once withdrawn, an
 * applicant cannot re-apply to the same posting. Jobs have no capacity/waitlist reason to allow
 * re-entry the way a cancelled event registration does.
 */
@Entity
@Table(name = "job_applications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    /** Bare object key (e.g. job-applications/9/uuid.pdf) — never a resolved URL; see changeset 012. */
    @Column(name = "resume_key", nullable = false, length = 500)
    private String resumeKey;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 11)
    private JobApplicationStatus status = JobApplicationStatus.SUBMITTED;

    /** Optional note left by the employer on a decision, or by the applicant on withdrawal. */
    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
