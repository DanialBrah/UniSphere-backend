package com.unisphere.backend.commerce.events.dto.response;

import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import com.unisphere.backend.commerce.events.enums.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * List/feed/search projection. Drops description, onlineUrl and updatedAt relative to
 * {@link EventResponse}; everything else that remains is identical.
 */
public record EventSummaryResponse(
        Long id,
        EventOrganizerResponse organizer,
        EventCategory category,
        EventStatus status,
        String title,
        String coverImageUrl,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        boolean online,
        String venueName,
        BigDecimal latitude,
        BigDecimal longitude,
        EventRegistrationMode registrationMode,
        String externalRegistrationUrl,
        Integer maxCapacity,
        int registeredCount,
        int waitlistedCount,
        Integer availableSeats,
        Long universityId,
        EventRegistrationStatus viewerRegistrationStatus,
        boolean canModify,
        LocalDateTime createdAt
) {}
