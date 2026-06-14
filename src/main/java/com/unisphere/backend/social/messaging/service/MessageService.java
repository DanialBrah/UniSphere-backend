package com.unisphere.backend.social.messaging.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.common.exception.MessageNotFoundException;
import com.unisphere.backend.common.exception.NotConversationMemberException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.messaging.dto.request.MarkReadRequest;
import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import com.unisphere.backend.social.messaging.dto.response.MessageResponse;
import com.unisphere.backend.social.messaging.dto.response.ReadReceiptEvent;
import com.unisphere.backend.social.messaging.entity.Message;
import com.unisphere.backend.social.messaging.entity.MessageRead;
import com.unisphere.backend.social.messaging.enums.MessageType;
import com.unisphere.backend.social.messaging.repository.ConversationMemberRepository;
import com.unisphere.backend.social.messaging.repository.MessageReadRepository;
import com.unisphere.backend.social.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MessageService {

    private static final String CACHE_PREFIX = "msg:conv:";

    @Value("${messaging.history-ttl-hours:48}")
    private long historyTtlHours;

    @Value("${messaging.history-max-messages:100}")
    private long historyMaxMessages;

    private final MessageRepository messageRepository;
    private final MessageReadRepository messageReadRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public MessageResponse sendMessage(SendMessageRequest req, User currentUser) {
        assertMembership(req.conversationId(), currentUser.getId());

        Message message = new Message();
        message.setConversationId(req.conversationId());
        message.setSenderId(currentUser.getId());
        message.setContent(req.content() != null ? req.content() : "");
        message.setMsgType(req.msgType() != null ? req.msgType() : MessageType.TEXT);
        message.setMediaUrl(req.mediaUrl());

        // Validate replyToId if provided
        if (req.replyToId() != null && req.replyToId() > 0) {
            Message replyToMessage = messageRepository.findById(req.replyToId())
                    .orElseThrow(() -> new IllegalArgumentException("Reply target message not found: " + req.replyToId()));
            if (!replyToMessage.getConversationId().equals(req.conversationId())) {
                throw new IllegalArgumentException("Reply target message belongs to a different conversation");
            }
            message.setReplyToId(req.replyToId());
        } else {
            message.setReplyToId(null);
        }

        messageRepository.save(message);

        MessageResponse response = toResponse(message);

        writeToCache(req.conversationId(), response);

        messagingTemplate.convertAndSend("/topic/conversation/" + req.conversationId(), response);

        return response;
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getHistory(Long convId, User currentUser, Pageable pageable) {
        assertMembership(convId, currentUser.getId());

        List<MessageResponse> cached = readFromCache(convId);
        // Only use cache when fully populated so page boundaries align with DB sequence
        if (cached.size() == historyMaxMessages) {
            int pageSize = pageable.getPageSize();
            int start = pageable.getPageNumber() * pageSize;
            if (start < (int) historyMaxMessages) {
                int end = Math.min(start + pageSize, (int) historyMaxMessages);
                long dbTotal = messageRepository.countByConversationId(convId);
                return new PageImpl<>(cached.subList(start, end), pageable, dbTotal);
            }
        }

        return messageRepository
                .findByConversationIdOrderByCreatedAtDesc(convId, pageable)
                .map(this::toResponse);
    }

    public void deleteMessage(Long messageId, User currentUser) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        assertMembership(message.getConversationId(), currentUser.getId());

        if (!message.getSenderId().equals(currentUser.getId())) {
            throw new UnauthorizedActionException("You can only delete your own messages");
        }

        message.setDeletedAt(LocalDateTime.now());
        messageRepository.save(message);

        invalidateCache(message.getConversationId());

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversationId(),
                new java.util.HashMap<String, Object>() {{
                    put("type", "MESSAGE_DELETED");
                    put("messageId", messageId);
                    put("conversationId", message.getConversationId());
                }}
        );
    }

    public ReadReceiptEvent markRead(MarkReadRequest req, User currentUser) {
        assertMembership(req.conversationId(), currentUser.getId());

        // Use bulk query to fetch unread messages
        List<Message> unreadMessages = messageRepository.findUnreadMessagesForUser(
                req.conversationId(),
                req.lastReadMessageId(),
                currentUser.getId()
        );

        // Bulk create MessageRead entities
        List<MessageRead> messageReads = unreadMessages.stream()
                .map(m -> {
                    MessageRead read = new MessageRead();
                    read.setMessageId(m.getId());
                    read.setUserId(currentUser.getId());
                    return read;
                })
                .toList();

        // Bulk save
        if (!messageReads.isEmpty()) {
            messageReadRepository.saveAll(messageReads);
        }

        ReadReceiptEvent event = new ReadReceiptEvent(
                req.conversationId(),
                req.lastReadMessageId(),
                currentUser.getId(),
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSend("/topic/conversation/" + req.conversationId(), event);

        return event;
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private void writeToCache(Long convId, MessageResponse response) {
        try {
            String key = CACHE_PREFIX + convId;
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForList().rightPush(key, json);
            stringRedisTemplate.opsForList().trim(key, -historyMaxMessages, -1);
            stringRedisTemplate.expire(key, historyTtlHours, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache message for conversation {}: {}", convId, e.getMessage());
        }
    }

    private List<MessageResponse> readFromCache(Long convId) {
        try {
            String key = CACHE_PREFIX + convId;
            List<String> entries = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (entries == null || entries.isEmpty()) return Collections.emptyList();
            stringRedisTemplate.expire(key, historyTtlHours, TimeUnit.HOURS);
            return entries.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, MessageResponse.class);
                        } catch (JsonProcessingException e) {
                            return null;
                        }
                    })
                    .filter(r -> r != null)
                    .toList();
        } catch (Exception e) {
            log.warn("Redis read failed for conversation {}: {}", convId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void invalidateCache(Long convId) {
        stringRedisTemplate.delete(CACHE_PREFIX + convId);
    }

    // ── Other helpers ─────────────────────────────────────────────────────────

    private void assertMembership(Long convId, Long userId) {
        if (!memberRepository.existsByConversationIdAndUserId(convId, userId)) {
            throw new NotConversationMemberException();
        }
    }

    MessageResponse toResponse(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);
        String name = sender == null ? "Unknown" : ConversationService.resolveDisplayName(sender);
        String avatar = sender == null ? null : sender.getAvatarUrl();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                name,
                avatar,
                message.getContent(),
                message.getMsgType(),
                message.getMediaUrl(),
                message.getReplyToId(),
                message.getCreatedAt()
        );
    }
}
