package com.unisphere.backend.commerce.events.dto.response;

import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import com.unisphere.backend.commerce.events.enums.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full detail view of an event. Unlike {@code LostFoundItemResponse}, nothing here is masked by
 * viewer — event locations are meant to be public and promotional.
 *
 * @param availableSeats         {@code maxCapacity - registeredCount}, or null when unlimited
 * @param viewerRegistrationStatus the caller's own registration on this event, or null if they have none
 * @param canModify              whether the caller may edit, change status or delete this event
 */
public record EventResponse(
        Long id,
        EventOrganizerResponse organizer,
        EventCategory category,
        EventStatus status,
        String title,
        String description,
        String coverImageUrl,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        boolean online,
        String onlineUrl,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
