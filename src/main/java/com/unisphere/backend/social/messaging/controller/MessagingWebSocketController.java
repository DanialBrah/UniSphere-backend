package com.unisphere.backend.social.messaging.controller;

import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.messaging.dto.request.MarkReadRequest;
import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import com.unisphere.backend.social.messaging.dto.response.MessageResponse;
import com.unisphere.backend.social.messaging.dto.response.ReadReceiptEvent;
import com.unisphere.backend.social.messaging.service.MessageService;
import com.unisphere.backend.social.messaging.service.TypingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessagingWebSocketController {

    private final MessageService messageService;
    private final TypingService typingService;

    @MessageMapping("message.send")
    public MessageResponse send(@Valid @Payload SendMessageRequest req, Principal principal) {
        User currentUser = extractUser(principal);
        return messageService.sendMessage(req, currentUser);
    }

    @MessageMapping("message.read")
    public ReadReceiptEvent markRead(@Valid @Payload MarkReadRequest req, Principal principal) {
        User currentUser = extractUser(principal);
        return messageService.markRead(req, currentUser);
    }

    @MessageMapping("typing")
    public void typing(@Valid @Payload TypingPayload payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User currentUser = extractUser(principal);
        typingService.broadcast(payload.conversationId(), payload.typing(), currentUser);
    }

    private User extractUser(Principal principal) {
        if (!(principal instanceof Authentication auth)) {
            log.error("Principal is not an Authentication instance: {}", principal.getClass().getName());
            throw new AccessDeniedException("Invalid authentication principal");
        }
        Object principalObj = auth.getPrincipal();
        if (!(principalObj instanceof User user)) {
            log.error("Authentication principal is not a User instance: {}", principalObj.getClass().getName());
            throw new AccessDeniedException("Invalid user principal");
        }
        return user;
    }

    public record TypingPayload(@NotNull Long conversationId, boolean typing) {}
}
