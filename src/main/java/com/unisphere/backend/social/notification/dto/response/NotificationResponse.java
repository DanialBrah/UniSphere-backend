package com.unisphere.backend.social.notification.dto.response;

import com.unisphere.backend.social.notification.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * {@code actorName} / {@code actorAvatarUrl} let the client render "Jane Tan liked your post" with
 * a real avatar instead of a generic "Someone". They are resolved with one batched lookup per page
 * rather than per row — see {@code NotificationService.loadActors}.
 * <p>
 * Both are null when the actor's account no longer exists; clients fall back to "Someone".
 */
public record NotificationResponse(
        Long id,
        Long actorId,
        String actorName,
        String actorAvatarUrl,
        NotificationType notifType,
        Long targetId,
        String targetType,
        boolean read,
        LocalDateTime createdAt
) {}
