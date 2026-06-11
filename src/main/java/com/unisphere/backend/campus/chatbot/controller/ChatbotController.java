package com.unisphere.backend.campus.chatbot.controller;

import com.unisphere.backend.campus.chatbot.dto.internal.ChatTurn;
import com.unisphere.backend.campus.chatbot.dto.request.ChatRequest;
import com.unisphere.backend.campus.chatbot.dto.response.ChatResponse;
import com.unisphere.backend.campus.chatbot.service.ChatbotService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campus/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.chat(req, currentUser)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatTurn>>> getHistory(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.getSessionHistory(currentUser.getId())));
    }

    @DeleteMapping("/session")
    public ResponseEntity<ApiResponse<Void>> clearSession(
            @AuthenticationPrincipal User currentUser) {
        chatbotService.clearSession(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Session cleared"));
    }
}
