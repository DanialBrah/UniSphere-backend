package com.unisphere.backend.common.exception;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(Long id) {
        super("Job not found: " + id);
    }
}
