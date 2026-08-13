package com.unisphere.backend.commerce.events.dto.response;

import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The minimum a map needs to draw one marker. Deliberately thin — a viewport can return hundreds of
 * these. Always {@code status == PUBLISHED}, since {@code EventRepository.findInViewport} never
 * returns anything else. Mirrors {@code LostFoundMapPinResponse}.
 */
public record EventMapPinResponse(
        Long id,
        String title,
        EventCategory category,
        EventStatus status,
        LocalDateTime startDatetime,
        BigDecimal latitude,
        BigDecimal longitude,
        String coverImageUrl,
        Integer maxCapacity,
        int registeredCount
) {}
