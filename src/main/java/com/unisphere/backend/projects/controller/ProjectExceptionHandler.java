package com.unisphere.backend.projects.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.InvalidProjectApplicationTransitionException;
import com.unisphere.backend.common.exception.InvalidProjectStatusTransitionException;
import com.unisphere.backend.common.exception.ProjectApplicationNotFoundException;
import com.unisphere.backend.common.exception.ProjectNotFoundException;
import com.unisphere.backend.common.exception.ProjectRoleNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Projects exception mappings, split out of {@code GlobalExceptionHandler} to keep that class
 * under Sonar's S6539 dependency ceiling — same reason {@code JobExceptionHandler} was split out.
 *
 * <p>The {@code @Order} is load-bearing. {@code GlobalExceptionHandler} carries an
 * {@code @ExceptionHandler(Exception.class)} catch-all pinned to {@code LOWEST_PRECEDENCE}; this
 * advice must sit ahead of it or every 404/409 below turns into a 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ProjectExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotFound(ProjectNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("PROJECT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProjectRoleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectRoleNotFound(ProjectRoleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("PROJECT_ROLE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProjectApplicationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationNotFound(ProjectApplicationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("PROJECT_APPLICATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidProjectStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStatusTransition(InvalidProjectStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_PROJECT_STATUS_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidProjectApplicationTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidApplicationTransition(
            InvalidProjectApplicationTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_PROJECT_APPLICATION_TRANSITION", ex.getMessage()));
    }
}
