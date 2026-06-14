package com.unisphere.backend.social.messaging.repository;

import com.unisphere.backend.social.messaging.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    long countByConversationId(Long conversationId);

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

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
