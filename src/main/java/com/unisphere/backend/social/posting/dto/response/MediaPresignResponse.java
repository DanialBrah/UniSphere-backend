package com.unisphere.backend.social.posting.dto.response;

public record MediaPresignResponse(
        String uploadUrl,
        String mediaKey
) {}
