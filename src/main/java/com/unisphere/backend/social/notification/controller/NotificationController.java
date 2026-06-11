package com.unisphere.backend.social.notification.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.notification.dto.response.NotificationResponse;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getNotifications(currentUser, pageable)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationService.getUnreadCount(currentUser))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        notificationService.markRead(id, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Notification marked as read"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal User currentUser) {
        notificationService.markAllRead(currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "All notifications marked as read"));
    }
}
