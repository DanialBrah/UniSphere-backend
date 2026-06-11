package com.unisphere.backend.social.messaging.controller;

import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.messaging.dto.request.MarkReadRequest;
import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import com.unisphere.backend.social.messaging.dto.response.MessageResponse;
import com.unisphere.backend.social.messaging.dto.response.ReadReceiptEvent;
import com.unisphere.backend.social.messaging.service.MessageService;
import com.unisphere.backend.social.messaging.service.TypingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class MessagingWebSocketController {

    private final MessageService messageService;
    private final TypingService typingService;

    @MessageMapping("message.send")
    public MessageResponse send(@Valid @Payload SendMessageRequest req, Principal principal) {
        User currentUser = (User) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return messageService.sendMessage(req, currentUser);
    }

    @MessageMapping("message.read")
    public ReadReceiptEvent markRead(@Valid @Payload MarkReadRequest req, Principal principal) {
        User currentUser = (User) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return messageService.markRead(req, currentUser);
    }

    @MessageMapping("typing")
    public void typing(@Payload TypingPayload payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User currentUser = (User) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal).getPrincipal();
        typingService.broadcast(payload.conversationId(), payload.typing(), currentUser);
    }

    public record TypingPayload(Long conversationId, boolean typing) {}
}
