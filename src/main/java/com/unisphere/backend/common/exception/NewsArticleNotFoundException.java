package com.unisphere.backend.common.exception;

/**
 * Also thrown when an article exists but the viewer may not see it — a 403 there would confirm
 * its existence. Same rule the posting module follows.
 */
public class NewsArticleNotFoundException extends RuntimeException {
    public NewsArticleNotFoundException(Long id) {
        super("News article not found: " + id);
    }
}
