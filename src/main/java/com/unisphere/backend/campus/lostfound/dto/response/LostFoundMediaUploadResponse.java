package com.unisphere.backend.campus.lostfound.dto.response;

public record LostFoundMediaUploadResponse(
        String mediaKey,
        String mediaUrl,
        String mediaType
) {}
