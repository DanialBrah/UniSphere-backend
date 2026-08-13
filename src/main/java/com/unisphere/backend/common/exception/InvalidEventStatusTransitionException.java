package com.unisphere.backend.common.exception;

import com.unisphere.backend.commerce.events.enums.EventStatus;

public class InvalidEventStatusTransitionException extends RuntimeException {

    public InvalidEventStatusTransitionException(EventStatus from, EventStatus to) {
        super("Cannot move an event from " + from + " to " + to);
    }

    public InvalidEventStatusTransitionException(String message) {
        super(message);
    }
}
