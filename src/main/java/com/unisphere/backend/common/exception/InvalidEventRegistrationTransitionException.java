package com.unisphere.backend.common.exception;

import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;

public class InvalidEventRegistrationTransitionException extends RuntimeException {

    public InvalidEventRegistrationTransitionException(EventRegistrationStatus from, EventRegistrationStatus to) {
        super("Cannot move an event registration from " + from + " to " + to);
    }

    public InvalidEventRegistrationTransitionException(String message) {
        super(message);
    }
}
