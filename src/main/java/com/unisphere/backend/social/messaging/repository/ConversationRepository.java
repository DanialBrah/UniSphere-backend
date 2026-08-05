package com.unisphere.backend.social.messaging.repository;

import com.unisphere.backend.social.messaging.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            SELECT c FROM Conversation c
            JOIN ConversationMember cm ON cm.conversationId = c.id
            WHERE cm.userId = :userId
            ORDER BY c.createdAt DESC
            """)
    Page<Conversation> findAllByMemberId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT c FROM Conversation c
            JOIN ConversationMember cm ON cm.conversationId = c.id
            WHERE c.id = :convId AND cm.userId = :userId
            """)
    Optional<Conversation> findByIdAndMemberId(@Param("convId") Long convId, @Param("userId") Long userId);

    Optional<Conversation> findByCommunityId(Long communityId);

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.convType = 'DIRECT'
              AND :userId1 <> :userId2
              AND EXISTS (
                  SELECT 1 FROM ConversationMember cm1
                  WHERE cm1.conversationId = c.id AND cm1.userId = :userId1
              )
              AND EXISTS (
                  SELECT 1 FROM ConversationMember cm2
                  WHERE cm2.conversationId = c.id AND cm2.userId = :userId2
              )
            """)
    Optional<Conversation> findDirectConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
