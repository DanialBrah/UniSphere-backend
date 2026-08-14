package com.unisphere.backend.commerce.services.service;

import com.unisphere.backend.commerce.services.entity.ServiceListing;
import com.unisphere.backend.commerce.services.entity.ServiceOrder;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.User;
import org.springframework.stereotype.Service;

/**
 * Who may create a listing, act on it, and order it. {@code commerce} depends only on
 * {@code common}, {@code config} and {@code identity} — never {@code social.posting}/{@code campus}
 * — so {@code viewerUniversityId} is copied rather than imported, the same identical-copy
 * convention {@code JobAccessService}/{@code ProjectAccessService} follow.
 */
@Service
public class ServiceAccessService {

    /** The viewer's university, or null. Employer/University/Admin accounts have no affiliation. */
    public Long viewerUniversityId(User user) {
        if (user instanceof Student s) return s.getUniversityId();
        if (user instanceof Alumni a) return a.getUniversityId();
        if (user instanceof Club c) return c.getUniversityId();
        return null;
    }

    /** Only STUDENT, ALUMNI and CLUB accounts may create a listing — matches {@code ProjectAccessService.assertCanCreate}. */
    public void assertCanCreate(User user) {
        Role role = user.getRole();
        if (role != Role.STUDENT && role != Role.ALUMNI && role != Role.CLUB) {
            throw new UnauthorizedActionException("Only students, alumni and clubs can create service listings");
        }
    }

    /**
     * Everyone except ADMIN may order a service — deliberately broader than
     * {@code assertCanCreate}/{@code JobAccessService.assertCanApply}: ordering is a purchase
     * action, not "apply to work/join a team", so an EMPLOYER hiring a student designer or a
     * UNIVERSITY department hiring a tutor are both plausible real customers. ADMIN accounts are
     * platform staff, not marketplace participants.
     */
    public void assertCanOrder(User user) {
        if (user.getRole() == Role.ADMIN) {
            throw new UnauthorizedActionException("Admin accounts cannot order services");
        }
    }

    public boolean isOwner(ServiceListing listing, User viewer) {
        return listing.getProviderId().equals(viewer.getId());
    }

    public boolean isOwnerOrAdmin(ServiceListing listing, User viewer) {
        return isOwner(listing, viewer) || viewer.getRole() == Role.ADMIN;
    }

    /** Editing, status changes, deletion and viewing the order roster are the provider's or an admin's. */
    public void assertCanModify(ServiceListing listing, User viewer) {
        if (!isOwnerOrAdmin(listing, viewer)) {
            throw new UnauthorizedActionException("Only the provider who created this listing can modify it");
        }
    }

    /** True if the caller is the order's client, the listing's provider, or an admin. */
    public boolean isPartyOrAdmin(ServiceOrder order, ServiceListing listing, User viewer) {
        return order.getClientId().equals(viewer.getId()) || isOwnerOrAdmin(listing, viewer);
    }

    public void assertCanManageOrder(ServiceOrder order, ServiceListing listing, User viewer) {
        if (!isPartyOrAdmin(order, listing, viewer)) {
            throw new UnauthorizedActionException("Only the order's client, the listing's provider, or an admin can act on this order");
        }
    }
}
