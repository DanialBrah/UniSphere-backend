package com.unisphere.backend.common.exception;

import com.unisphere.backend.projects.enums.ProjectApplicationStatus;

public class InvalidProjectApplicationTransitionException extends RuntimeException {

    public InvalidProjectApplicationTransitionException(ProjectApplicationStatus from, ProjectApplicationStatus to) {
        super("Cannot move a project application from " + from + " to " + to);
    }
}
