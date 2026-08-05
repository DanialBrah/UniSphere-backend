package com.unisphere.backend.campus.news.dto.response;

public record NewsAuthorResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String role
) {}
