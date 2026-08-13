package com.unisphere.backend.common.exception;

import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;

public class InvalidJobApplicationTransitionException extends RuntimeException {

    public InvalidJobApplicationTransitionException(JobApplicationStatus from, JobApplicationStatus to) {
        super("Cannot move a job application from " + from + " to " + to);
    }

    public InvalidJobApplicationTransitionException(String message) {
        super(message);
    }
}
