package com.unisphere.backend.common.exception;

import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;

public class InvalidServiceOrderTransitionException extends RuntimeException {

    public InvalidServiceOrderTransitionException(ServiceOrderStatus from, ServiceOrderStatus to) {
        super("Cannot move a service order from " + from + " to " + to);
    }

    public InvalidServiceOrderTransitionException(String message) {
        super(message);
    }
}
