package com.unisphere.backend.common.exception;

public class EventRegistrationNotFoundException extends RuntimeException {
    public EventRegistrationNotFoundException(Long id) {
        super("Event registration not found: " + id);
    }
}
