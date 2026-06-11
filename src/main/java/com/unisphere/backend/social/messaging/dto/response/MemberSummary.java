package com.unisphere.backend.social.messaging.dto.response;

import com.unisphere.backend.social.messaging.enums.MemberRole;

public record MemberSummary(
        Long userId,
        String displayName,
        String avatarUrl,
        MemberRole role
) {}
