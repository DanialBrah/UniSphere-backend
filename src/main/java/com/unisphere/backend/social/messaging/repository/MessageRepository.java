package com.unisphere.backend.social.messaging.repository;

import com.unisphere.backend.social.messaging.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    long countByConversationId(Long conversationId);

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    /**
     * Batched form of {@link #findTopByConversationIdOrderByCreatedAtDesc} — the latest message for
     * each of several conversations in one query. Ranks on MAX(id) rather than createdAt: ids are
     * auto-increment so they agree on ordering, and unlike createdAt they cannot tie.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.id IN (
                SELECT MAX(m2.id) FROM Message m2
                WHERE m2.conversationId IN :conversationIds
                GROUP BY m2.conversationId
            )
            """)
    List<Message> findLatestPerConversation(@Param("conversationIds") Collection<Long> conversationIds);

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.id <= :lastReadMessageId
              AND m.senderId <> :userId
              AND NOT EXISTS (
                  SELECT 1 FROM MessageRead mr
                  WHERE mr.messageId = m.id AND mr.userId = :userId
              )
            """)
    List<Message> findUnreadMessagesForUser(
            @Param("conversationId") Long conversationId,
            @Param("lastReadMessageId") Long lastReadMessageId,
            @Param("userId") Long userId
    );
}
