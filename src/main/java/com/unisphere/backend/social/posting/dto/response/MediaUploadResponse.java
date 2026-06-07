package com.unisphere.backend.social.posting.dto.response;

public record MediaUploadResponse(
        String mediaKey,
        String mediaUrl,
        String mediaType
) {}
