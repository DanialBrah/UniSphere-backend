package com.unisphere.backend.campus.lostfound.dto.response;

import java.util.List;

/**
 * A suggested counterpart for a report.
 *
 * @param score   0-100, combining category, distance, date proximity and text overlap
 * @param reasons human-readable justifications ("Same category (ELECTRONICS)", "420 m away") so the
 *                client can explain the suggestion rather than showing a bare number
 */
public record LostFoundMatchResponse(
        LostFoundItemSummaryResponse item,
        int score,
        List<String> reasons
) {}
