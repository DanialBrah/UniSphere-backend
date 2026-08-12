package com.unisphere.backend.campus.lostfound.dto.request;

import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A new lost-item or found-item report.
 *
 * <p>There is deliberately no {@code universityId} field: it is always derived server-side from the
 * reporter's own affiliation. A request field the service conditionally ignores is a rule you have
 * to re-read the service to trust.
 *
 * <p>Three cross-field rules cannot be expressed with bean validation and are enforced in
 * {@code LostFoundService}, surfacing as 400 {@code BAD_REQUEST}:
 * <ol>
 *   <li>each coordinate pair must be fully present or fully absent;</li>
 *   <li>a {@code FOUND} report must carry a pickup location — a non-blank {@code pickupPlace} or
 *       pickup coordinates. "Where can I collect it" is the point of a found report;</li>
 *   <li>on a {@code LOST} report the pickup fields are allowed and mean "where to return it to me".</li>
 * </ol>
 *
 * @param identifyingDetail a detail only the true owner would know, withheld from every claimant so
 *                          the reporter has something to adjudicate claims against
 */
public record CreateLostFoundItemRequest(

        @NotNull(message = "itemType is required")
        LostFoundItemType itemType,

        /* Null means OTHER — a report filed in a hurry shouldn't be blocked by a taxonomy question. */
        LostFoundCategory category,

        @NotBlank(message = "Title is required")
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

        // @Digits mirrors DECIMAL(10,7) exactly: without it a client sending 15 decimal places is
        // silently truncated by MySQL, or 500s on a data-truncation error instead of getting a
        // clean 400. @DecimalMin/@DecimalMax catch a swapped lat/lng pair at the edge rather than
        // dropping a pin in the Indian Ocean.
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

        @NotNull(message = "occurredAt is required")
        @PastOrPresent(message = "occurredAt cannot be in the future")
        LocalDateTime occurredAt,

        @Size(max = 10, message = "An item may have at most 10 media files")
        List<@Valid LostFoundMediaItem> media
) {}
