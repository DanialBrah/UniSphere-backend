package com.unisphere.backend.common.exception;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(Long id) {
        super("Project not found: " + id);
    }
}
