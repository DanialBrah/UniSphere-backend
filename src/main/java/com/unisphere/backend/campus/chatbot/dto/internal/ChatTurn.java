package com.unisphere.backend.campus.chatbot.dto.internal;

import java.time.LocalDateTime;

public record ChatTurn(String role, String text, LocalDateTime timestamp) {}
