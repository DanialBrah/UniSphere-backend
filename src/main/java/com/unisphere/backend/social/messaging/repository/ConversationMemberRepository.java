package com.unisphere.backend.social.messaging.repository;

import com.unisphere.backend.social.messaging.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    List<ConversationMember> findByConversationId(Long conversationId);

    /** Batched form of {@link #findByConversationId} for rendering a page of conversations. */
    List<ConversationMember> findByConversationIdIn(Collection<Long> conversationIds);

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    long countByConversationId(Long conversationId);

    void deleteByConversationIdAndUserId(Long conversationId, Long userId);
}
