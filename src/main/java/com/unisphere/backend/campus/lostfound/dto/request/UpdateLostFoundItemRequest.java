package com.unisphere.backend.campus.lostfound.dto.request;

import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Partial update. Every field is nullable and the service applies {@code if (req.x() != null)}, so
 * omitting a field leaves it untouched. Passing a blank string clears the nullable text fields —
 * the {@code UpdateNewsArticleRequest.coverImageKey} precedent.
 *
 * <p>A coordinate cannot be "blank", so clearing a location needs the explicit
 * {@code clearIncidentLocation} / {@code clearPickupLocation} flags rather than a magic value.
 *
 * <p>There is no {@code status} field: the whole lifecycle lives in
 * {@code PATCH /items/{id}/status} so the transition table has exactly one implementation.
 * There is no {@code itemType} either — flipping LOST to FOUND after claims exist would invalidate
 * every one of them.
 */
public record UpdateLostFoundItemRequest(

        LostFoundCategory category,

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Size(max = 500, message = "Identifying detail must not exceed 500 characters")
        String identifyingDetail,

        @Size(max = 500)
        String primaryImageKey,

        @Size(max = 255, message = "Incident place must not exceed 255 characters")
        String incidentPlace,

        @DecimalMin(value = "-90.0", message = "incidentLatitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "incidentLatitude must be between -90 and 90")
        @Digits(integer = 3, fraction = 7, message = "incidentLatitude supports at most 7 decimal places")
        BigDecimal incidentLatitude,

        @DecimalMin(value = "-180.0", message = "incidentLongitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "incidentLongitude must be between -180 and 180")
        @Digits(integer = 3, fraction = 7, message = "incidentLongitude supports at most 7 decimal places")
        BigDecimal incidentLongitude,

        @Size(max = 255, message = "Pickup place must not exceed 255 characters")
        String pickupPlace,

        @DecimalMin(value = "-90.0", message = "pickupLatitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "pickupLatitude must be between -90 and 90")
        @Digits(integer = 3, fraction = 7, message = "pickupLatitude supports at most 7 decimal places")
        BigDecimal pickupLatitude,

        @DecimalMin(value = "-180.0", message = "pickupLongitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "pickupLongitude must be between -180 and 180")
        @Digits(integer = 3, fraction = 7, message = "pickupLongitude supports at most 7 decimal places")
        BigDecimal pickupLongitude,

        @Size(max = 500, message = "Pickup instructions must not exceed 500 characters")
        String pickupInstructions,

        @PastOrPresent(message = "occurredAt cannot be in the future")
        LocalDateTime occurredAt,

        /** True nulls both incident coordinates. Ignored when incidentLatitude is also supplied. */
        Boolean clearIncidentLocation,

        /** True nulls both pickup coordinates. Ignored when pickupLatitude is also supplied. */
        Boolean clearPickupLocation,

        List<Long> removeMediaIds,

        @Size(max = 10, message = "An item may have at most 10 media files")
        List<@Valid LostFoundMediaItem> addMedia
) {}
