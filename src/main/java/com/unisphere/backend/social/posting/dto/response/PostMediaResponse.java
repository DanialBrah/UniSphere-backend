package com.unisphere.backend.social.posting.dto.response;

import com.unisphere.backend.social.posting.enums.MediaType;

public record PostMediaResponse(
        Long id,
        String mediaUrl,
        MediaType mediaType,
        int sortOrder
) {}
