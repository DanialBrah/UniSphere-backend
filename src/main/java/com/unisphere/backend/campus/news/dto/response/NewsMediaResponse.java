package com.unisphere.backend.campus.news.dto.response;

import com.unisphere.backend.campus.news.enums.NewsMediaType;

public record NewsMediaResponse(
        Long id,
        String mediaUrl,
        NewsMediaType mediaType,
        int sortOrder
) {}
