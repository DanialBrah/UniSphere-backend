package com.unisphere.backend.commerce.jobs.service;

import com.unisphere.backend.commerce.jobs.entity.Job;
import com.unisphere.backend.commerce.jobs.entity.JobApplication;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.University;
import com.unisphere.backend.identity.entity.User;
import org.springframework.stereotype.Service;

/**
 * Who may post a job, act on it, and apply to it. Stricter posting gate than
 * {@code EventAccessService}: events allow any organizer role, jobs are EMPLOYER-only.
 *
 * <p>{@code commerce} depends only on {@code common}, {@code config} and {@code identity} — never
 * {@code social}/{@code campus} — so {@code viewerUniversityId} is copied rather than imported, the
 * same identical-copy convention {@code EventAccessService} follows.
 */
@Service
public class JobAccessService {

    /** The viewer's university, or null. Employer and Admin accounts have no affiliation. */
    public Long viewerUniversityId(User user) {
        if (user instanceof Student s) return s.getUniversityId();
        if (user instanceof Alumni a) return a.getUniversityId();
        if (user instanceof Club c) return c.getUniversityId();
        if (user instanceof University u) return u.getId();
        return null; // Employer, Admin — no university affiliation
    }

    /** Only EMPLOYER accounts may post a job — see the module's deliberate deviation from Events. */
    public void assertCanPost(User user) {
        if (user.getRole() != Role.EMPLOYER) {
            throw new UnauthorizedActionException("Only employer accounts can post jobs");
        }
    }

    /** Only STUDENT and ALUMNI accounts may apply. */
    public void assertCanApply(User user) {
        if (user.getRole() != Role.STUDENT && user.getRole() != Role.ALUMNI) {
            throw new UnauthorizedActionException("Only students and alumni can apply for jobs");
        }
    }

    public boolean isOwner(Job job, User viewer) {
        return job.getEmployerId().equals(viewer.getId());
    }

    public boolean isOwnerOrAdmin(Job job, User viewer) {
        return isOwner(job, viewer) || viewer.getRole() == Role.ADMIN;
    }

    /** Editing, status changes, deletion and viewing the applicant roster are the employer's or an admin's. */
    public void assertCanModify(Job job, User viewer) {
        if (!isOwnerOrAdmin(job, viewer)) {
            throw new UnauthorizedActionException("Only the employer who posted this job can modify it");
        }
    }

    /** Employer-side review decisions (REVIEWED/SHORTLISTED/REJECTED/HIRED) — never the applicant's own. */
    public void assertCanManageApplication(JobApplication application, Job job, User viewer) {
        if (!isOwnerOrAdmin(job, viewer)) {
            throw new UnauthorizedActionException("Only the job's employer can update this application's status");
        }
    }
}
