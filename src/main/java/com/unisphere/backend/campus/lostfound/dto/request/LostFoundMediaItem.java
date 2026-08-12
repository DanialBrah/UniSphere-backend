package com.unisphere.backend.campus.lostfound.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * One gallery entry on a create/update request. {@code mediaKey} must be a key the caller obtained
 * from the presign or upload endpoint — {@code LostFoundMediaService.assertOwnedKey} rejects
 * anything under another user's prefix, on attach as well as on delete.
 */
public record LostFoundMediaItem(

        @NotBlank(message = "mediaKey is required")
        String mediaKey,

        @NotBlank(message = "mediaType is required")
        @Pattern(regexp = "IMAGE|VIDEO", message = "mediaType must be IMAGE or VIDEO")
        String mediaType
) {}
