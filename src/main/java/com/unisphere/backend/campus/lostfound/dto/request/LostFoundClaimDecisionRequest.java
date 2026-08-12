package com.unisphere.backend.campus.lostfound.dto.request;

import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Resolves a pending claim. The caller's permitted values depend on who they are:
 * {@code APPROVED}/{@code REJECTED} for the item's reporter or an admin, {@code CANCELLED} for the
 * claimant. {@code PENDING} is never a valid target and yields a 409.
 */
public record LostFoundClaimDecisionRequest(

        @NotNull(message = "status is required")
        LostFoundClaimStatus status,

        @Size(max = 500, message = "Decision note must not exceed 500 characters")
        String decisionNote
) {}
