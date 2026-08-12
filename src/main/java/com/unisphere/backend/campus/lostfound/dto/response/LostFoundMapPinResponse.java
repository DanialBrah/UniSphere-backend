package com.unisphere.backend.campus.lostfound.dto.response;

import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;

import java.math.BigDecimal;

/**
 * The minimum a map needs to draw one marker. Deliberately thin: a viewport can return hundreds of
 * these, and the client fetches the full item only when a pin is opened.
 *
 * <p>Subject to the same coordinate masking as every other view — {@code coordinatesApproximate}
 * tells the client to draw an uncertainty circle instead of a point.
 */
public record LostFoundMapPinResponse(
        Long id,
        LostFoundItemType itemType,
        LostFoundItemStatus status,
        LostFoundCategory category,
        String title,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean coordinatesApproximate,
        String thumbnailUrl
) {}
