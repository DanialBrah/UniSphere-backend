package com.unisphere.backend.social.messaging.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.messaging.dto.request.AddMemberRequest;
import com.unisphere.backend.social.messaging.dto.request.CreateConversationRequest;
import com.unisphere.backend.social.messaging.dto.response.ConversationResponse;
import com.unisphere.backend.social.messaging.dto.response.MemberSummary;
import com.unisphere.backend.social.messaging.service.ConversationService;
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
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> create(
            @Valid @RequestBody CreateConversationRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(conversationService.createConversation(req, currentUser), "Conversation created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ConversationResponse>>> getInbox(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getInbox(currentUser, pageable)));
    }

    @GetMapping("/{convId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
            @PathVariable Long convId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getConversation(convId, currentUser)));
    }

    @GetMapping("/{convId}/members")
    public ResponseEntity<ApiResponse<java.util.List<MemberSummary>>> getMembers(
            @PathVariable Long convId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getMembers(convId, currentUser)));
    }

    @PostMapping("/{convId}/members")
    public ResponseEntity<ApiResponse<MemberSummary>> addMember(
            @PathVariable Long convId,
            @Valid @RequestBody AddMemberRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(conversationService.addMember(convId, req, currentUser), "Member added"));
    }

    @DeleteMapping("/{convId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable Long convId,
            @AuthenticationPrincipal User currentUser) {
        conversationService.deleteConversation(convId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Conversation deleted"));
    }

    @PutMapping("/{convId}/members/{userId}/promote")
    public ResponseEntity<ApiResponse<MemberSummary>> promoteMember(
            @PathVariable Long convId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                conversationService.promoteMember(convId, userId, currentUser), "Member promoted to admin"));
    }

    @DeleteMapping("/{convId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long convId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        conversationService.removeMember(convId, userId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Member removed"));
    }
}
