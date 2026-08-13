package com.unisphere.backend.commerce.events.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.EventNotFoundException;
import com.unisphere.backend.common.exception.EventRegistrationNotFoundException;
import com.unisphere.backend.common.exception.InvalidEventRegistrationTransitionException;
import com.unisphere.backend.common.exception.InvalidEventStatusTransitionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Events exception mappings, split out of {@code GlobalExceptionHandler} to keep that class under
 * Sonar's S6539 dependency ceiling — same reason {@code LostFoundExceptionHandler} was split out.
 *
 * <p>The {@code @Order} is load-bearing. {@code GlobalExceptionHandler} carries an
 * {@code @ExceptionHandler(Exception.class)} catch-all pinned to {@code LOWEST_PRECEDENCE}; this
 * advice must sit ahead of it or every 404/409 below turns into a 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class EventExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEventNotFound(EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("EVENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(EventRegistrationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRegistrationNotFound(EventRegistrationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("EVENT_REGISTRATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidEventStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStatusTransition(InvalidEventStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_EVENT_STATUS_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidEventRegistrationTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRegistrationTransition(
            InvalidEventRegistrationTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_EVENT_REGISTRATION_TRANSITION", ex.getMessage()));
    }
}
