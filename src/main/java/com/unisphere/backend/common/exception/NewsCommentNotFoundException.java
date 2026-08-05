package com.unisphere.backend.common.exception;

public class NewsCommentNotFoundException extends RuntimeException {
    public NewsCommentNotFoundException(Long id) {
        super("News comment not found: " + id);
    }
}
