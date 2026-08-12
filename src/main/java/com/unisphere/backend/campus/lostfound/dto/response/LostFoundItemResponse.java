package com.unisphere.backend.campus.lostfound.dto.response;

import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full detail view of a report.
 *
 * <p>Several fields are viewer-dependent and may be null or coarsened even when the underlying row
 * has a value — see {@code LostFoundAccessService.locationViewFor} for the exact matrix. In short,
 * for a FOUND item a viewer with no approved claim gets rounded coordinates, no pickup location and
 * an empty gallery.
 *
 * @param coordinatesApproximate true when the coordinates were coarsened for this viewer; render an
 *                               uncertainty circle rather than a false-precision pin
 * @param identifyingDetail      the reporter's withheld adjudication secret — non-null only for the
 *                               reporter themselves and admins
 * @param viewerClaimStatus      the caller's own claim on this item, or null if they have none
 * @param canModify              whether the caller may edit, change status or delete this item
 */
public record LostFoundItemResponse(
        Long id,
        LostFoundUserResponse reporter,
        LostFoundItemType itemType,
        LostFoundCategory category,
        LostFoundItemStatus status,
        String title,
        String description,
        String identifyingDetail,
        String primaryImageUrl,
        String incidentPlace,
        BigDecimal incidentLatitude,
        BigDecimal incidentLongitude,
        boolean coordinatesApproximate,
        String pickupPlace,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String pickupInstructions,
        Long universityId,
        LocalDateTime occurredAt,
        LocalDateTime resolvedAt,
        long pendingClaimCount,
        LostFoundClaimStatus viewerClaimStatus,
        boolean canModify,
        List<LostFoundMediaResponse> media,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
