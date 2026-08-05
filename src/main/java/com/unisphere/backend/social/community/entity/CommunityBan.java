package com.unisphere.backend.social.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_bans")
@Getter
@Setter
@NoArgsConstructor
public class CommunityBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "banned_by", nullable = false)
    private Long bannedBy;

    @Column(length = 500)
    private String reason;

    @Column(name = "banned_at", nullable = false, updatable = false)
    private LocalDateTime bannedAt = LocalDateTime.now();
}
