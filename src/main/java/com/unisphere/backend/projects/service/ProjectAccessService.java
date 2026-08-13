package com.unisphere.backend.projects.service;

import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.projects.entity.Project;
import com.unisphere.backend.projects.entity.ProjectApplication;
import org.springframework.stereotype.Service;

/**
 * Who may create a project, act on it, and apply to it. {@code projects} depends only on
 * {@code common}, {@code config} and {@code identity} — never {@code commerce}/{@code social} —
 * so {@code viewerUniversityId} is copied rather than imported, the same identical-copy convention
 * {@code JobAccessService}/{@code EventAccessService} follow.
 */
@Service
public class ProjectAccessService {

    /** The viewer's university, or null. Employer/University/Admin accounts have no affiliation. */
    public Long viewerUniversityId(User user) {
        if (user instanceof Student s) return s.getUniversityId();
        if (user instanceof Alumni a) return a.getUniversityId();
        if (user instanceof Club c) return c.getUniversityId();
        return null;
    }

    /** Only STUDENT, ALUMNI and CLUB accounts may showcase a project. */
    public void assertCanCreate(User user) {
        Role role = user.getRole();
        if (role != Role.STUDENT && role != Role.ALUMNI && role != Role.CLUB) {
            throw new UnauthorizedActionException("Only students, alumni and clubs can create projects");
        }
    }

    /** Only STUDENT and ALUMNI accounts may apply to join — matches {@code JobAccessService.assertCanApply}. */
    public void assertCanApply(User user) {
        if (user.getRole() != Role.STUDENT && user.getRole() != Role.ALUMNI) {
            throw new UnauthorizedActionException("Only students and alumni can apply to join a project");
        }
    }

    public boolean isOwner(Project project, User viewer) {
        return project.getOwnerId().equals(viewer.getId());
    }

    public boolean isOwnerOrAdmin(Project project, User viewer) {
        return isOwner(project, viewer) || viewer.getRole() == Role.ADMIN;
    }

    /** Editing, status changes, deletion, role management and viewing the applicant roster are the owner's or an admin's. */
    public void assertCanModify(Project project, User viewer) {
        if (!isOwnerOrAdmin(project, viewer)) {
            throw new UnauthorizedActionException("Only the project's owner can modify it");
        }
    }

    /** Owner-side review decisions (ACCEPTED/REJECTED) — never the applicant's own. */
    public void assertCanManageApplication(ProjectApplication application, Project project, User viewer) {
        if (!isOwnerOrAdmin(project, viewer)) {
            throw new UnauthorizedActionException("Only the project's owner can update this application's status");
        }
    }
}
