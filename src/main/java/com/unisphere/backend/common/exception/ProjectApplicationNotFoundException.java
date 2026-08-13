package com.unisphere.backend.common.exception;

public class ProjectApplicationNotFoundException extends RuntimeException {
    public ProjectApplicationNotFoundException(Long id) {
        super("Project application not found: " + id);
    }
}
