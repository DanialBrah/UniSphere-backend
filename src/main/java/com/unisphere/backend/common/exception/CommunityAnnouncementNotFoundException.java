package com.unisphere.backend.common.exception;

public class CommunityAnnouncementNotFoundException extends RuntimeException {
    public CommunityAnnouncementNotFoundException(Long id) {
        super("Community announcement not found: " + id);
    }
}
