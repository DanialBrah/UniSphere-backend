package com.unisphere.backend.common.exception;

public class CommunityJoinRequestNotFoundException extends RuntimeException {
    public CommunityJoinRequestNotFoundException(Long id) {
        super("Community join request not found: " + id);
    }
}
