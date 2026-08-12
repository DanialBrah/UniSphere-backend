package com.unisphere.backend.campus.news.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.InvalidNewsStatusTransitionException;
import com.unisphere.backend.common.exception.NewsArticleNotFoundException;
import com.unisphere.backend.common.exception.NewsAuthoringNotAllowedException;
import com.unisphere.backend.common.exception.NewsCommentNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Campus news exception mappings. Status codes and error codes are unchanged from when these lived
 * in {@code GlobalExceptionHandler}.
 *
 * <p>See {@code LostFoundExceptionHandler} for why the {@code @Order} matters.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class NewsExceptionHandler {

    @ExceptionHandler(NewsArticleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNewsArticleNotFound(NewsArticleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NEWS_ARTICLE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(NewsCommentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNewsCommentNotFound(NewsCommentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NEWS_COMMENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(NewsAuthoringNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleNewsAuthoringNotAllowed(NewsAuthoringNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("NEWS_AUTHORING_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidNewsStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidNewsStatusTransition(
            InvalidNewsStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_NEWS_STATUS_TRANSITION", ex.getMessage()));
    }
}
