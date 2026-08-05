package com.unisphere.backend.common.exception;

public class NotCommunityMemberException extends RuntimeException {
    public NotCommunityMemberException() {
        super("You are not a member of this community");
    }
}
