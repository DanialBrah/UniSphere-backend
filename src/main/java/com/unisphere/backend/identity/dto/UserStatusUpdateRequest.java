package com.unisphere.backend.identity.dto;

import com.unisphere.backend.identity.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull UserStatus status
) {}
