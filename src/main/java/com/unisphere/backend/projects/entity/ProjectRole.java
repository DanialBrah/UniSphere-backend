package com.unisphere.backend.projects.entity;

import com.unisphere.backend.projects.enums.ProjectRoleStatus;
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
 * A structured open position a project is recruiting for (e.g. "2x Backend Developer"). Applicants
 * apply to a specific role — see {@code ProjectApplication} — not the project generically.
 *
 * <p>{@link #filledCount} is a capacity counter, not a monotonic total like {@code Job.applicationCount}:
 * it increments when an application is accepted and decrements when that member later leaves —
 * closer to {@code Event.registeredCount} in shape. {@link #status} auto-closes once
 * {@code filledCount >= slots} and reopens if a member leaves — see {@code ProjectService}.
 */
@Entity
@Table(name = "project_roles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ProjectRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int slots = 1;

    @Column(name = "filled_count", nullable = false)
    private int filledCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private ProjectRoleStatus status = ProjectRoleStatus.OPEN;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
