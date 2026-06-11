package com.unisphere.backend.social.messaging.repository;

import com.unisphere.backend.social.messaging.entity.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {

    boolean existsByMessageIdAndUserId(Long messageId, Long userId);

    List<MessageRead> findByMessageIdIn(List<Long> messageIds);
}
