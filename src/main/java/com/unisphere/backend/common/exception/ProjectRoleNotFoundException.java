package com.unisphere.backend.common.exception;

public class ProjectRoleNotFoundException extends RuntimeException {
    public ProjectRoleNotFoundException(Long id) {
        super("Project role not found: " + id);
    }
}
