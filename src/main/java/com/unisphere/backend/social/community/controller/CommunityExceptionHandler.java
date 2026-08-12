package com.unisphere.backend.social.community.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.CommunityAnnouncementNotFoundException;
import com.unisphere.backend.common.exception.CommunityJoinRequestNotFoundException;
import com.unisphere.backend.common.exception.CommunityNotFoundException;
import com.unisphere.backend.common.exception.NotCommunityMemberException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Community exception mappings. Status codes and error codes are unchanged from when these lived in
 * {@code GlobalExceptionHandler}.
 *
 * <p>See {@code LostFoundExceptionHandler} for why the {@code @Order} matters.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CommunityExceptionHandler {

    @ExceptionHandler(CommunityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommunityNotFound(CommunityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("COMMUNITY_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(NotCommunityMemberException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotCommunityMember(NotCommunityMemberException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("NOT_COMMUNITY_MEMBER", ex.getMessage()));
    }

    @ExceptionHandler(CommunityAnnouncementNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommunityAnnouncementNotFound(
            CommunityAnnouncementNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("COMMUNITY_ANNOUNCEMENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CommunityJoinRequestNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommunityJoinRequestNotFound(
            CommunityJoinRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("COMMUNITY_JOIN_REQUEST_NOT_FOUND", ex.getMessage()));
    }
}
