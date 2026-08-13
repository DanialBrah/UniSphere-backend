package com.unisphere.backend.projects.entity;

import com.unisphere.backend.projects.enums.ProjectMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Active team roster row — post-acceptance only, no invite state. Flat shape deliberately mirrors
 * {@code CommunityMember} rather than the old skeleton's INVITED/ACCEPTED/DECLINED status: leaving
 * or removal is a hard delete of this row, no membership history kept for v1.
 */
@Entity
@Table(name = "project_members")
@Getter
@Setter
@NoArgsConstructor
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Which role this member filled. Null for the owner's own row. */
    @Column(name = "project_role_id")
    private Long projectRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 11)
    private ProjectMemberRole role = ProjectMemberRole.CONTRIBUTOR;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();
}
