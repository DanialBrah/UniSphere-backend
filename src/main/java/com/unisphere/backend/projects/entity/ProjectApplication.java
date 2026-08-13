package com.unisphere.backend.projects.entity;

import com.unisphere.backend.projects.enums.ProjectApplicationStatus;
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
 * One applicant's application to one project role. Audit history — never soft-deleted, so this
 * entity carries no {@code @SQLRestriction}; self-withdrawal is {@code status = WITHDRAWN}, matching
 * {@code JobApplication}.
 *
 * <p>Carries a hard {@code UNIQUE(project_role_id, applicant_id)} constraint (see migration
 * {@code 020}) — same tradeoff {@code job_applications} makes: once decided, an applicant cannot
 * re-apply to the same role. Applying to a <em>different</em> role on the same project is unaffected.
 */
@Entity
@Table(name = "project_applications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ProjectApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_role_id", nullable = false)
    private Long projectRoleId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private ProjectApplicationStatus status = ProjectApplicationStatus.PENDING;

    /** Optional note left by the owner on a decision, or by the applicant on withdrawal. */
    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
