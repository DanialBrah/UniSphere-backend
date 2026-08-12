package com.unisphere.backend.common.exception;

public class LostFoundClaimNotFoundException extends RuntimeException {
    public LostFoundClaimNotFoundException(Long id) {
        super("Lost & found claim not found: " + id);
    }
}
