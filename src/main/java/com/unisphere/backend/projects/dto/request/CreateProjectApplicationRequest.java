package com.unisphere.backend.projects.dto.request;

import jakarta.validation.constraints.Size;

/** @param message optional note to the project owner explaining why you'd be a good fit */
public record CreateProjectApplicationRequest(

        @Size(max = 500, message = "message must not exceed 500 characters")
        String message
) {}
