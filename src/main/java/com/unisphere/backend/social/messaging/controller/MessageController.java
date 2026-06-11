package com.unisphere.backend.social.messaging.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.messaging.dto.request.MarkReadRequest;
import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import com.unisphere.backend.social.messaging.dto.response.MessageResponse;
import com.unisphere.backend.social.messaging.dto.response.ReadReceiptEvent;
import com.unisphere.backend.social.messaging.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @Valid @RequestBody SendMessageRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(messageService.sendMessage(req, currentUser), "Message sent"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getHistory(
            @RequestParam Long conversationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(messageService.getHistory(conversationId, currentUser, pageable)));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long messageId,
            @AuthenticationPrincipal User currentUser) {
        messageService.deleteMessage(messageId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Message deleted"));
    }

    @PostMapping("/read")
    public ResponseEntity<ApiResponse<ReadReceiptEvent>> markRead(
            @Valid @RequestBody MarkReadRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(messageService.markRead(req, currentUser), "Messages marked as read"));
    }
}
