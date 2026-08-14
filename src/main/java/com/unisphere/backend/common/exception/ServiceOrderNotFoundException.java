package com.unisphere.backend.common.exception;

public class ServiceOrderNotFoundException extends RuntimeException {
    public ServiceOrderNotFoundException(Long id) {
        super("Service order not found: " + id);
    }
}
