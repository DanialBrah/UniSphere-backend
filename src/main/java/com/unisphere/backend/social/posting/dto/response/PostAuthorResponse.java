package com.unisphere.backend.social.posting.dto.response;

public record PostAuthorResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String role
) {}
