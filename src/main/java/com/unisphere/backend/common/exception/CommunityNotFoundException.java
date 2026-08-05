package com.unisphere.backend.common.exception;

public class CommunityNotFoundException extends RuntimeException {
    public CommunityNotFoundException(Long id) {
        super("Community not found: " + id);
    }
}
