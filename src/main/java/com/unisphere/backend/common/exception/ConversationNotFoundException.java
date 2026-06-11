package com.unisphere.backend.common.exception;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(Long id) {
        super("Conversation not found: " + id);
    }
}
