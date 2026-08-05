package com.unisphere.backend.common.exception;

import com.unisphere.backend.campus.news.enums.NewsStatus;

public class InvalidNewsStatusTransitionException extends RuntimeException {
    public InvalidNewsStatusTransitionException(NewsStatus from, NewsStatus to) {
        super("Cannot move a news article from " + from + " to " + to);
    }
}
