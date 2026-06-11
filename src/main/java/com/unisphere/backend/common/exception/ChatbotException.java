package com.unisphere.backend.common.exception;

public class ChatbotException extends RuntimeException {
    public ChatbotException(String message) {
        super(message);
    }

    public ChatbotException(String message, Throwable cause) {
        super(message, cause);
    }
}
