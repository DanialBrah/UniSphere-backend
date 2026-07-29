package com.unisphere.backend.social.notification.service;

import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.notification.dto.response.NotificationResponse;
import com.unisphere.backend.social.notification.entity.Notification;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final MediaUrlResolver mediaUrlResolver;

    record NotificationCreatedEvent(Long notificationId, Long userId) {}

    // Persist only — use when no real-time push is needed
    public Notification create(Long userId, Long actorId, NotificationType type, Long targetId, String targetType) {
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setActorId(actorId);
        notif.setNotifType(type);
        notif.setTargetId(targetId);
        notif.setTargetType(targetType);
        return notificationRepository.save(notif);
    }

    // Persist + push via WebSocket — use for real-time events
    public void createAndPush(Long userId, Long actorId, NotificationType type, Long targetId, String targetType) {
        if (userId.equals(actorId)) return;
        Notification notif = create(userId, actorId, type, targetId, targetType);
        eventPublisher.publishEvent(new NotificationCreatedEvent(notif.getId(), userId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        notificationRepository.findById(event.notificationId()).ifPresent(notif ->
                userRepository.findById(event.userId()).ifPresent(recipient -> {
                    // One notification here, so a direct lookup rather than the batch path below
                    User actor = notif.getActorId() == null
                            ? null
                            : userRepository.findById(notif.getActorId()).orElse(null);
                    messagingTemplate.convertAndSendToUser(
                            recipient.getEmail(),
                            "/queue/notifications",
                            toResponse(notif, actor)
                    );
                })
        );
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(User currentUser, Pageable pageable) {
        Page<Notification> page = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        Map<Long, User> actors = loadActors(page.getContent());
        return page.map(n -> toResponse(n, actors.get(n.getActorId())));
    }

    /**
     * Resolves every distinct actor on the page in one query — looking each up per row would make
     * a 20-row page cost 20 extra round trips.
     */
    private Map<Long, User> loadActors(List<Notification> notifications) {
        Set<Long> actorIds = notifications.stream()
                .map(Notification::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (actorIds.isEmpty()) return Map.of();
        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User currentUser) {
        return notificationRepository.countByUserIdAndReadFalse(currentUser.getId());
    }

    public void markRead(Long notificationId, User currentUser) {
        Notification notif = notificationRepository.findByIdAndUserId(notificationId, currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        notif.setRead(true);
        notificationRepository.save(notif);
    }

    public void markAllRead(User currentUser) {
        notificationRepository.markAllReadByUserId(currentUser.getId());
    }

    /** {@code actor} may be null when the account has since been deleted — clients show "Someone". */
    NotificationResponse toResponse(Notification n, User actor) {
        return new NotificationResponse(
                n.getId(),
                n.getActorId(),
                actor == null ? null : UserService.resolveDisplayName(actor),
                actor == null ? null : mediaUrlResolver.toViewableUrl(actor.getAvatarUrl()),
                n.getNotifType(),
                n.getTargetId(),
                n.getTargetType(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
