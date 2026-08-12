package com.unisphere.backend.campus.lostfound.dto.response;

import com.unisphere.backend.campus.lostfound.enums.LostFoundMediaType;

/**
 * A gallery entry. The entity holds a bare key; {@code mediaUrl} is a presigned URL minted per read
 * by {@code LostFoundMapper}.
 */
public record LostFoundMediaResponse(
        Long id,
        String mediaUrl,
        LostFoundMediaType mediaType,
        int sortOrder
) {}
