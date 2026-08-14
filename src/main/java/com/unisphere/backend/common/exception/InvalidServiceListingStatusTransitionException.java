package com.unisphere.backend.common.exception;

import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;

public class InvalidServiceListingStatusTransitionException extends RuntimeException {

    public InvalidServiceListingStatusTransitionException(ServiceListingStatus from, ServiceListingStatus to) {
        super("Cannot move a service listing from " + from + " to " + to);
    }
}
