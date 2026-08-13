package com.unisphere.backend.commerce.events.service;

import com.unisphere.backend.commerce.events.entity.Event;
import com.unisphere.backend.commerce.events.entity.EventRegistration;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.University;
import com.unisphere.backend.identity.entity.User;
import org.springframework.stereotype.Service;

/**
 * Who may act on an event, and event visibility. Simpler than {@code LostFoundAccessService}: event
 * locations are meant to be public and promotional — the whole point of putting them on a map — so
 * there is no coordinate-coarsening concern here.
 *
 * <p>Mirrors the shape of {@code LostFoundAccessService}/{@code NewsAccessService} for consistency
 * across modules. {@code commerce} depends only on {@code common}, {@code config} and
 * {@code identity} — never {@code social}/{@code campus} — so {@code viewerUniversityId} is copied
 * rather than imported, the fourth identical copy of this method in the codebase.
 */
@Service
public class EventAccessService {

    /**
     * The viewer's university, or null. University itself resolves to its own id — deliberately not
     * a helper that returns null for a University account, which would leave a university's own
     * event invisible to its own students.
     */
    public Long viewerUniversityId(User user) {
        if (user instanceof Student s) return s.getUniversityId();
        if (user instanceof Alumni a) return a.getUniversityId();
        if (user instanceof Club c) return c.getUniversityId();
        if (user instanceof University u) return u.getId();
        return null; // Employer, Admin — no university affiliation
    }

    /**
     * The universityId to stamp on an event, derived from its organizer. Never read from the
     * request — there is no such request field. An organizer with no affiliation (Employer, Admin)
     * yields null, which the feed predicate treats as visible to everyone.
     */
    public Long resolveEventUniversityId(User organizer) {
        return viewerUniversityId(organizer);
    }

    public boolean isOwner(Event event, User viewer) {
        return event.getOrganizerId().equals(viewer.getId());
    }

    public boolean isOwnerOrAdmin(Event event, User viewer) {
        return isOwner(event, viewer) || viewer.getRole() == Role.ADMIN;
    }

    /** Editing, status changes, deletion and viewing the attendee roster are the organizer's or an admin's. */
    public void assertCanModify(Event event, User viewer) {
        if (!isOwnerOrAdmin(event, viewer)) {
            throw new UnauthorizedActionException("Only the organizer can modify this event");
        }
    }

    /** Cancelling a registration is the registrant's own, or the event organizer's/an admin's. */
    public void assertCanManageRegistration(EventRegistration registration, Event event, User viewer) {
        boolean isRegistrant = registration.getUserId().equals(viewer.getId());
        if (!isRegistrant && !isOwnerOrAdmin(event, viewer)) {
            throw new UnauthorizedActionException("You may not modify this registration");
        }
    }

    /** Checking a ticket in at the door is organizer/admin only — never self-service. */
    public void assertCanCheckIn(Event event, User viewer) {
        if (!isOwnerOrAdmin(event, viewer)) {
            throw new UnauthorizedActionException("Only the organizer can check in attendees");
        }
    }
}
