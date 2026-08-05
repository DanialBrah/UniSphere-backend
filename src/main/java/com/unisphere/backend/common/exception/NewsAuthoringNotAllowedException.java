package com.unisphere.backend.common.exception;

import com.unisphere.backend.identity.entity.Role;

/** Only UNIVERSITY, EMPLOYER, CLUB and ADMIN accounts may author news; everyone else reads. */
public class NewsAuthoringNotAllowedException extends RuntimeException {
    public NewsAuthoringNotAllowedException(Role role) {
        super("Role " + role + " may not author news articles");
    }
}
