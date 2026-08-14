package com.unisphere.backend.commerce.services.dto.request;

import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import jakarta.validation.constraints.NotNull;

public record ServiceListingStatusUpdateRequest(

        @NotNull(message = "status is required")
        ServiceListingStatus status
) {}
