package com.unisphere.backend.campus.lostfound.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "This is mine, and here's why."
 *
 * <p>The 20-character floor on {@code proofText} is cheap anti-spam and forces a claim the reporter
 * can actually adjudicate against the item's withheld {@code identifyingDetail} — "its mine" is not
 * a claim anyone can act on.
 */
public record CreateLostFoundClaimRequest(

        @NotBlank(message = "Proof of ownership is required")
        @Size(min = 20, max = 1000,
                message = "Describe what proves the item is yours (at least 20 characters)")
        String proofText,

        @Size(max = 500)
        String proofImageKey
) {}
