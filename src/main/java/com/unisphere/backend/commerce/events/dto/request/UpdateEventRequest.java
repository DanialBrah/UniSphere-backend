package com.unisphere.backend.commerce.events.dto.request;

import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Partial update. Every field is nullable and the service applies {@code if (req.x() != null)}, so
 * omitting a field leaves it untouched. Passing a blank string clears a nullable text field — the
 * {@code UpdateLostFoundItemRequest} precedent.
 *
 * <p>A coordinate/venue trio and {@code maxCapacity} cannot themselves be "blank", so clearing them
 * needs the explicit {@code clearLocation}/{@code clearMaxCapacity} flags rather than a magic value.
 *
 * <p>There is no {@code status} field: the whole lifecycle lives in
 * {@code PATCH /events/{id}/status} so the transition table has exactly one implementation. The five
 * cross-field rules from {@link CreateEventRequest} are re-checked against the post-update state.
 */
public record UpdateEventRequest(

        EventCategory category,

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        LocalDateTime startDatetime,

        LocalDateTime endDatetime,

        @Size(max = 500)
        String coverImageKey,

        /** When set, switches the event between online and physical. Null leaves it unchanged. */
        Boolean online,

        @Size(max = 500, message = "onlineUrl must not exceed 500 characters")
        String onlineUrl,

        @Size(max = 255, message = "venueName must not exceed 255 characters")
        String venueName,

        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        @Digits(integer = 3, fraction = 7, message = "latitude supports at most 7 decimal places")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        @Digits(integer = 3, fraction = 7, message = "longitude supports at most 7 decimal places")
        BigDecimal longitude,

        /** True clears venueName and both coordinates. Ignored when venueName/latitude are also supplied. */
        Boolean clearLocation,

        EventRegistrationMode registrationMode,

        @Size(max = 500, message = "externalRegistrationUrl must not exceed 500 characters")
        String externalRegistrationUrl,

        @Positive(message = "maxCapacity must be positive")
        Integer maxCapacity,

        /** True sets maxCapacity back to unlimited. Ignored when maxCapacity is also supplied. */
        Boolean clearMaxCapacity
) {}
