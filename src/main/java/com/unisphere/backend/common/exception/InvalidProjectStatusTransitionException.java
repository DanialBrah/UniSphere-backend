package com.unisphere.backend.common.exception;

import com.unisphere.backend.projects.enums.ProjectStatus;

public class InvalidProjectStatusTransitionException extends RuntimeException {

    public InvalidProjectStatusTransitionException(ProjectStatus from, ProjectStatus to) {
        super("Cannot move a project from " + from + " to " + to);
    }
}
