package com.unisphere.backend.projects.entity;

import com.unisphere.backend.projects.enums.ProjectStatus;
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

import java.time.LocalDateTime;

/**
 * A showcased project, posted by a {@code STUDENT}/{@code ALUMNI}/{@code CLUB} account — see
 * {@code ProjectAccessService.assertCanCreate}.
 *
 * <p>Unlike {@code Job.universityId}, which an {@code Employer} sets explicitly because it has no
 * affiliation of its own, {@link #universityId} IS derived from the owner's own affiliation at
 * creation time (every allowed owner role carries one) — matching {@code Event.universityId} rather
 * than {@code Job.universityId}. Null means visible to every university.
 *
 * <p>{@link #isRecruiting} is an owner-controlled pause/resume switch, independent of each
 * {@link ProjectRole}'s own {@code status} — a project can have open role slots with recruiting
 * paused, or the reverse.
 */
@Entity
@Table(name = "projects")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Null = visible to all universities. Derived from the owner's own affiliation, never set explicitly. */
    @Column(name = "university_id")
    private Long universityId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Bare object key, never a resolved URL — see changeset 012. */
    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 11)
    private ProjectStatus status = ProjectStatus.OPEN;

    @Column(name = "is_recruiting", nullable = false)
    private boolean recruiting = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isRecruiting() {
        return recruiting;
    }
}
