package com.unisphere.backend.projects.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Patch semantics throughout: a null field leaves the current value untouched; a blank string
 * clears an optional text field — same convention as {@code UpdateJobRequest}. {@code isRecruiting}
 * needs no clear flag: it's a plain non-nullable toggle on the entity, so null simply means "leave
 * as-is" and there is nothing to clear it to.
 */
public record UpdateProjectRequest(

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Size(max = 500)
        String coverImageKey,

        @Size(max = 500, message = "githubUrl must not exceed 500 characters")
        String githubUrl,

        @Size(max = 500, message = "demoUrl must not exceed 500 characters")
        String demoUrl,

        Boolean isRecruiting
) {}
