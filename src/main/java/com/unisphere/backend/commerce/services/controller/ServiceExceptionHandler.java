package com.unisphere.backend.commerce.services.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.InvalidServiceListingStatusTransitionException;
import com.unisphere.backend.common.exception.InvalidServiceOrderTransitionException;
import com.unisphere.backend.common.exception.ServiceListingNotFoundException;
import com.unisphere.backend.common.exception.ServiceOrderNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Services exception mappings, split out of {@code GlobalExceptionHandler} to keep that class
 * under Sonar's S6539 dependency ceiling — same reason {@code JobExceptionHandler}/
 * {@code ProjectExceptionHandler} were split out.
 *
 * <p>The {@code @Order} is load-bearing. {@code GlobalExceptionHandler} carries an
 * {@code @ExceptionHandler(Exception.class)} catch-all pinned to {@code LOWEST_PRECEDENCE}; this
 * advice must sit ahead of it or every 404/403 below turns into a 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ServiceExceptionHandler {

    @ExceptionHandler(ServiceListingNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleListingNotFound(ServiceListingNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("SERVICE_LISTING_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ServiceOrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderNotFound(ServiceOrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("SERVICE_ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidServiceListingStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidListingTransition(InvalidServiceListingStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_SERVICE_LISTING_STATUS_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidServiceOrderTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOrderTransition(InvalidServiceOrderTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_SERVICE_ORDER_TRANSITION", ex.getMessage()));
    }
}
