package com.unisphere.backend.commerce.events.dto.request;

import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A new event. Always created {@code DRAFT} — publishing is an explicit later action via
 * {@code PATCH /events/{id}/status}.
 *
 * <p>There is deliberately no {@code universityId} field: it is always derived server-side from the
 * organizer's own affiliation, same rule as {@code CreateLostFoundItemRequest}. There is no
 * {@code status} field either.
 *
 * <p>Five cross-field rules cannot be expressed with bean validation and are enforced in
 * {@code EventService}, surfacing as 400 {@code BAD_REQUEST}:
 * <ol>
 *   <li>{@code endDatetime} must be strictly after {@code startDatetime};</li>
 *   <li>{@code online = true} requires {@code onlineUrl} and forbids {@code venueName}/coordinates;</li>
 *   <li>{@code online = false} requires {@code venueName} AND both coordinates, and forbids {@code onlineUrl};</li>
 *   <li>{@code registrationMode = EXTERNAL} requires {@code externalRegistrationUrl} and forbids {@code maxCapacity};</li>
 *   <li>{@code registrationMode = INTERNAL} forbids {@code externalRegistrationUrl}.</li>
 * </ol>
 */
public record CreateEventRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        /* Null means OTHER — matches CreateLostFoundItemRequest.category. */
        EventCategory category,

        @NotNull(message = "startDatetime is required")
        @Future(message = "startDatetime must be in the future")
        LocalDateTime startDatetime,

        @NotNull(message = "endDatetime is required")
        LocalDateTime endDatetime,

        @Size(max = 500)
        String coverImageKey,

        boolean online,

        @Size(max = 500, message = "onlineUrl must not exceed 500 characters")
        String onlineUrl,

        @Size(max = 255, message = "venueName must not exceed 255 characters")
        String venueName,

        // @Digits mirrors DECIMAL(10,7) exactly — see CreateLostFoundItemRequest's identical note.
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        @Digits(integer = 3, fraction = 7, message = "latitude supports at most 7 decimal places")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        @Digits(integer = 3, fraction = 7, message = "longitude supports at most 7 decimal places")
        BigDecimal longitude,

        @NotNull(message = "registrationMode is required")
        EventRegistrationMode registrationMode,

        @Size(max = 500, message = "externalRegistrationUrl must not exceed 500 characters")
        String externalRegistrationUrl,

        /* Null means unlimited. */
        @Positive(message = "maxCapacity must be positive")
        Integer maxCapacity
) {}
