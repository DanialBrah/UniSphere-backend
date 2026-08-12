package com.unisphere.backend.common.exception;

public class LostFoundItemNotFoundException extends RuntimeException {
    public LostFoundItemNotFoundException(Long id) {
        super("Lost & found item not found: " + id);
    }
}
