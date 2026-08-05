package com.unisphere.backend.social.community.entity;

import com.unisphere.backend.social.community.enums.CommunityVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "communities")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** Non-null only for UNIVERSITY_ONLY communities; see CommunityAccessService. */
    @Column(name = "university_id")
    private Long universityId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Bare object key (e.g. communities/9/uuid.jpeg) — never a resolved URL; see changeset 012. */
    @Column(name = "banner_key", length = 500)
    private String bannerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CommunityVisibility visibility = CommunityVisibility.PUBLIC;

    @Column(name = "member_count", nullable = false)
    private int memberCount = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
