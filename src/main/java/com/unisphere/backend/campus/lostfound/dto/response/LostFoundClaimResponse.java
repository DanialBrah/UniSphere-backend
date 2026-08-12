package com.unisphere.backend.campus.lostfound.dto.response;

import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;

import java.time.LocalDateTime;

/**
 * A claim as seen by the claimant, the item's reporter, or an admin — nobody else can read one.
 *
 * @param proofImageUrl presigned per read; null when the claimant attached no photo
 * @param reviewedBy    null while PENDING, and after a claimant-driven CANCELLED
 */
public record LostFoundClaimResponse(
        Long id,
        Long itemId,
        String itemTitle,
        LostFoundUserResponse claimant,
        LostFoundClaimStatus status,
        String proofText,
        String proofImageUrl,
        String decisionNote,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
