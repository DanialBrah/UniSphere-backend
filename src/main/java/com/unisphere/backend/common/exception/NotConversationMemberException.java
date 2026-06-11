package com.unisphere.backend.common.exception;

public class NotConversationMemberException extends RuntimeException {
    public NotConversationMemberException() {
        super("You are not a member of this conversation");
    }
}
