package com.unisphere.backend.social.notification.dto.response;

import com.unisphere.backend.social.notification.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long actorId,
        NotificationType notifType,
        Long targetId,
        String targetType,
        boolean read,
        LocalDateTime createdAt
) {}
