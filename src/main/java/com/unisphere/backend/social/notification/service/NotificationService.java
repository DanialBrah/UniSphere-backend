package com.unisphere.backend.social.notification.service;

import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

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
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        notificationRepository.findById(event.notificationId()).ifPresent(notif ->
                userRepository.findById(event.userId()).ifPresent(user ->
                        messagingTemplate.convertAndSendToUser(
                                user.getEmail(),
                                "/queue/notifications",
                                toResponse(notif)
                        )
                )
        );
    }

@Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(User currentUser, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
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

    NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getActorId(), n.getNotifType(),
                n.getTargetId(), n.getTargetType(), n.isRead(), n.getCreatedAt()
        );
    }
}
