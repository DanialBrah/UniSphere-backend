package com.unisphere.backend.social.community.dto.request;

import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull CommunityMemberRole role
) {}
