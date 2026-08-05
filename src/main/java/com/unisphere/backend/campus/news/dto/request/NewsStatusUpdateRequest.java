package com.unisphere.backend.campus.news.dto.request;

import com.unisphere.backend.campus.news.enums.NewsStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * @param status      the target state
 * @param scheduledAt only meaningful with status = DRAFT — queues the article for auto-publish
 */
public record NewsStatusUpdateRequest(

        @NotNull(message = "Status is required")
        NewsStatus status,

        @Future(message = "scheduledAt must be in the future")
        LocalDateTime scheduledAt
) {}
