package com.unisphere.backend.common.exception;

import com.unisphere.backend.commerce.jobs.enums.JobStatus;

public class InvalidJobStatusTransitionException extends RuntimeException {

    public InvalidJobStatusTransitionException(JobStatus from, JobStatus to) {
        super("Cannot move a job from " + from + " to " + to);
    }

    public InvalidJobStatusTransitionException(String message) {
        super(message);
    }
}
