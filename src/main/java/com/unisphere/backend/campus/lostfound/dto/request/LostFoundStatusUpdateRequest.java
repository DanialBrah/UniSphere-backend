package com.unisphere.backend.campus.lostfound.dto.request;

import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Drives the item lifecycle. {@code CLAIMED} and {@code EXPIRED} are rejected here with a 409 —
 * the first is written only by claim approval, the second only by the scheduled sweep. See
 * {@link LostFoundItemStatus}.
 */
public record LostFoundStatusUpdateRequest(

        @NotNull(message = "status is required")
        LostFoundItemStatus status,

        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note
) {}
