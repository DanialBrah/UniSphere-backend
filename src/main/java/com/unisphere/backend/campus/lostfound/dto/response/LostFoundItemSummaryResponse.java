package com.unisphere.backend.campus.lostfound.dto.response;

import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * List/feed projection. Drops description, identifyingDetail, pickupInstructions, the gallery and
 * updatedAt relative to {@link LostFoundItemResponse}; the same privacy masking applies to
 * everything that remains.
 */
public record LostFoundItemSummaryResponse(
        Long id,
        LostFoundUserResponse reporter,
        LostFoundItemType itemType,
        LostFoundCategory category,
        LostFoundItemStatus status,
        String title,
        String primaryImageUrl,
        String incidentPlace,
        BigDecimal incidentLatitude,
        BigDecimal incidentLongitude,
        boolean coordinatesApproximate,
        String pickupPlace,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        Long universityId,
        LocalDateTime occurredAt,
        long pendingClaimCount,
        LostFoundClaimStatus viewerClaimStatus,
        boolean canModify,
        LocalDateTime createdAt
) {}
