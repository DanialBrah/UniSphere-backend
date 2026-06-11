package com.unisphere.backend.social.notification.service;

import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.notification.dto.response.NotificationResponse;
import com.unisphere.backend.social.notification.entity.Notification;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification create(Long userId, Long actorId, NotificationType type, Long targetId, String targetType) {
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setActorId(actorId);
        notif.setNotifType(type);
        notif.setTargetId(targetId);
        notif.setTargetType(targetType);
        return notificationRepository.save(notif);
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
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notif.getUserId().equals(currentUser.getId())) {
            throw new UnauthorizedActionException("Cannot mark another user's notification as read");
        }
        notif.setRead(true);
        notificationRepository.save(notif);
    }

    public void markAllRead(User currentUser) {
        notificationRepository.markAllReadByUserId(currentUser.getId());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getActorId(), n.getNotifType(),
                n.getTargetId(), n.getTargetType(), n.isRead(), n.getCreatedAt()
        );
    }
}
