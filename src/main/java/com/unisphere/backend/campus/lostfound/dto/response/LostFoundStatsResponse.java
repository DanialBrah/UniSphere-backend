package com.unisphere.backend.campus.lostfound.dto.response;

import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;

import java.util.Map;

/**
 * Board-level counts, scoped to what the caller can see (their university plus unaffiliated
 * reports; everything, for an admin). Every enum constant is present as a key, zero included, so a
 * dashboard doesn't have to null-check each bucket.
 */
public record LostFoundStatsResponse(
        long totalItems,
        Map<LostFoundItemType, Long> byType,
        Map<LostFoundItemStatus, Long> byStatus,
        Map<LostFoundCategory, Long> byCategory,
        long openClaims
) {}
