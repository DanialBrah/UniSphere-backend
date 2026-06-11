package com.unisphere.backend.social.messaging.entity;

import com.unisphere.backend.social.messaging.enums.MemberRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_members")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();
}
