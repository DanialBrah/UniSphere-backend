package com.unisphere.backend.common.exception;

public class JobApplicationNotFoundException extends RuntimeException {
    public JobApplicationNotFoundException(Long id) {
        super("Job application not found: " + id);
    }
}
