package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.InvalidLostFoundClaimTransitionException;
import com.unisphere.backend.common.exception.InvalidLostFoundStatusTransitionException;
import com.unisphere.backend.common.exception.LostFoundClaimNotFoundException;
import com.unisphere.backend.common.exception.LostFoundItemNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lost &amp; Found exception mappings, split out of {@code GlobalExceptionHandler} to keep that
 * class under Sonar's S6539 dependency ceiling.
 *
 * <p><b>The {@code @Order} is load-bearing.</b> Spring's {@code ExceptionHandlerExceptionResolver}
 * walks {@code @ControllerAdvice} beans in order and stops at the first one exposing a handler for
 * the thrown type. {@code GlobalExceptionHandler} carries an {@code @ExceptionHandler(Exception.class)}
 * catch-all, so if it were consulted first it would match everything here and turn every 404/409
 * below into a 500. It is pinned to {@code LOWEST_PRECEDENCE}; this advice must sit ahead of it.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class LostFoundExceptionHandler {

    @ExceptionHandler(LostFoundItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleItemNotFound(LostFoundItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("LOST_FOUND_ITEM_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(LostFoundClaimNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleClaimNotFound(LostFoundClaimNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("LOST_FOUND_CLAIM_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidLostFoundStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStatusTransition(
            InvalidLostFoundStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_LOST_FOUND_STATUS_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidLostFoundClaimTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidClaimTransition(
            InvalidLostFoundClaimTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_LOST_FOUND_CLAIM_TRANSITION", ex.getMessage()));
    }
}
