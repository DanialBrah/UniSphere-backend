package com.unisphere.backend.social.messaging.service;

import com.unisphere.backend.common.exception.NotConversationMemberException;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.messaging.dto.response.TypingEvent;
import com.unisphere.backend.social.messaging.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TypingService {

    private final ConversationMemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(Long conversationId, boolean typing, User currentUser) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, currentUser.getId())) {
            throw new NotConversationMemberException();
        }

        TypingEvent event = new TypingEvent(
                conversationId,
                currentUser.getId(),
                ConversationService.resolveDisplayName(currentUser),
                typing
        );

        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, event);
    }
}
