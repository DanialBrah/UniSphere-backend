package com.unisphere.backend.projects.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new project. Always created {@code OPEN} — unlike jobs there is no draft state, "showcase"
 * implies immediate visibility. {@code universityId} is never a request field: it's derived
 * server-side from the owner's own affiliation, see {@code ProjectAccessService.viewerUniversityId}.
 *
 * @param coverImageKey a key returned by {@code POST /projects/media/presign} or {@code /upload},
 *                      owned by the caller — see {@code ProjectMediaService.assertOwnedKey}
 */
public record CreateProjectRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Size(max = 500)
        String coverImageKey,

        @Size(max = 500, message = "githubUrl must not exceed 500 characters")
        String githubUrl,

        @Size(max = 500, message = "demoUrl must not exceed 500 characters")
        String demoUrl
) {}
