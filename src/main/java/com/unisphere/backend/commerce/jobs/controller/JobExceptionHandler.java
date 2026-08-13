package com.unisphere.backend.commerce.jobs.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.InvalidJobApplicationTransitionException;
import com.unisphere.backend.common.exception.InvalidJobStatusTransitionException;
import com.unisphere.backend.common.exception.JobApplicationNotFoundException;
import com.unisphere.backend.common.exception.JobNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Jobs exception mappings, split out of {@code GlobalExceptionHandler} to keep that class under
 * Sonar's S6539 dependency ceiling — same reason {@code EventExceptionHandler} was split out.
 *
 * <p>The {@code @Order} is load-bearing. {@code GlobalExceptionHandler} carries an
 * {@code @ExceptionHandler(Exception.class)} catch-all pinned to {@code LOWEST_PRECEDENCE}; this
 * advice must sit ahead of it or every 404/409 below turns into a 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class JobExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobNotFound(JobNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("JOB_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(JobApplicationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationNotFound(JobApplicationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("JOB_APPLICATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidJobStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStatusTransition(InvalidJobStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_JOB_STATUS_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidJobApplicationTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidApplicationTransition(
            InvalidJobApplicationTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_JOB_APPLICATION_TRANSITION", ex.getMessage()));
    }
}
