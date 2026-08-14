package com.unisphere.backend.common.exception;

public class ServiceListingNotFoundException extends RuntimeException {
    public ServiceListingNotFoundException(Long id) {
        super("Service listing not found: " + id);
    }
}
