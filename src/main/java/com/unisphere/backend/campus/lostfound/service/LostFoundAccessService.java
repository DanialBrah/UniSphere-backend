package com.unisphere.backend.campus.lostfound.service;

import com.unisphere.backend.campus.lostfound.entity.LostFoundItem;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.University;
import com.unisphere.backend.identity.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Who may act on a report, and how much of one a given viewer is allowed to see. Mirrors
 * {@code campus.news.service.NewsAccessService} and {@code social.community.service.CommunityAccessService}.
 *
 * <p>This class is the module's correctness core. Every response that renders a
 * {@link LostFoundItem} routes its location fields through {@link #locationViewFor} — see the note
 * on that method for why that matters.
 */
@Service
public class LostFoundAccessService {

    /**
     * Decimal places retained in a coarsened coordinate. 2 dp is roughly 1.1 km — enough to say
     * "somewhere on the main campus" without pinpointing the item.
     */
    private final int coarseScale;

    public LostFoundAccessService(
            @Value("${lost-found.coarse-coordinate-scale:2}") int coarseScale) {
        this.coarseScale = coarseScale;
    }

    /**
     * The viewer's university, or null.
     *
     * <p>Deliberately NOT {@code UserService.universityIdOf}: that helper returns null for a
     * University account, which would leave a university's own report invisible to its own
     * students. The {@code University u -> u.getId()} branch below is the whole difference.
     *
     * <p>This is the third identical copy of the method ({@code NewsAccessService},
     * {@code CommunityAccessService}). Copying is deliberate: {@code campus} currently depends only
     * on {@code common}, {@code config} and {@code identity}, and importing the community service
     * would create the first campus -> social edge for eight lines.
     */
    public Long viewerUniversityId(User user) {
        if (user instanceof Student s)    return s.getUniversityId();
        if (user instanceof Alumni a)     return a.getUniversityId();
        if (user instanceof Club c)       return c.getUniversityId();
        if (user instanceof University u) return u.getId();
        return null; // Employer, Admin — no university affiliation
    }

    /**
     * The universityId to stamp on a report, derived from its reporter. Never read from the request
     * — there is no such request field.
     *
     * <p>Unlike the news analogue this stamps unconditionally rather than only for a particular
     * visibility: a lost item on campus X is noise on campus Y, so scoping is the default. A
     * reporter with no affiliation (Employer, Admin) yields null, which the feed predicate treats
     * as visible to everyone.
     */
    public Long resolveItemUniversityId(User reporter) {
        return viewerUniversityId(reporter);
    }

    public boolean isOwner(LostFoundItem item, User viewer) {
        return item.getReportedBy().equals(viewer.getId());
    }

    public boolean isOwnerOrAdmin(LostFoundItem item, User viewer) {
        return isOwner(item, viewer) || viewer.getRole() == Role.ADMIN;
    }

    /** Editing, status changes and deletion are the reporter's or an admin's. */
    public void assertCanModify(LostFoundItem item, User viewer) {
        if (!isOwnerOrAdmin(item, viewer)) {
            throw new UnauthorizedActionException("Only the reporter can modify this item");
        }
    }

    /** Approving or rejecting a claim is the item reporter's or an admin's. */
    public void assertCanDecideClaim(LostFoundItem item, User viewer) {
        if (!isOwnerOrAdmin(item, viewer)) {
            throw new UnauthorizedActionException("Only the reporter can decide claims on this item");
        }
    }

    /**
     * The location fields as a given viewer is allowed to see them.
     *
     * <p><b>This is the privacy guard, and it is an imperative invariant rather than a database
     * rule.</b> It is called from exactly three private mapping methods in {@code LostFoundService}
     * — {@code toResponse}, {@code toSummaryResponse} and {@code toPin} — and every endpoint in the
     * module that renders an item routes through one of them. {@code LostFoundMatchService}
     * deliberately does not build its own DTOs for the same reason. Any future endpoint that
     * renders a {@link LostFoundItem} without going through those three leaks exact coordinates,
     * the pickup point and the full gallery, with no compile error to catch it —
     * {@code LostFoundPrivacyTest} carries one test per geo-exposing endpoint as the backstop.
     *
     * <p>The threat: a FOUND report that publishes both a metre-accurate pin and a full photo
     * gallery hands a stranger everything they need to walk up and collect someone else's property,
     * and to fabricate a convincing claim. So for an unprivileged viewer of a FOUND item the pickup
     * location disappears entirely, the incident coordinates are coarsened, and the gallery is
     * withheld. {@code incidentPlace} text stays — "where I found it" is the entire point of a
     * found report — as does {@code primaryImageKey}, so the item is still recognisable.
     *
     * <p>{@code identifyingDetail} is owner/admin-only at every claim status, including APPROVED:
     * it is the reporter's adjudication secret and there is no moment at which revealing it helps.
     *
     * <p>Coarsening is deterministic rounding, never random jitter. Jitter would let an attacker
     * average N repeated GETs back to the true point; rounding returns the same value every time.
     */
    public LocationView locationViewFor(LostFoundItem item, User viewer, boolean viewerHasApprovedClaim) {
        boolean ownerOrAdmin = isOwnerOrAdmin(item, viewer);
        boolean privileged = ownerOrAdmin || viewerHasApprovedClaim;

        // A LOST item's reporter wants maximum reach — nothing is withheld but the secret.
        if (item.getItemType() == LostFoundItemType.LOST || privileged) {
            return new LocationView(
                    item.getIncidentLatitude(),
                    item.getIncidentLongitude(),
                    false,
                    item.getPickupPlace(),
                    item.getPickupLatitude(),
                    item.getPickupLongitude(),
                    item.getPickupInstructions(),
                    ownerOrAdmin ? item.getIdentifyingDetail() : null,
                    true);
        }

        // FOUND item, viewer has no approved claim.
        return new LocationView(
                coarsen(item.getIncidentLatitude()),
                coarsen(item.getIncidentLongitude()),
                true,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    private BigDecimal coarsen(BigDecimal value) {
        return value == null ? null : value.setScale(coarseScale, RoundingMode.HALF_UP);
    }

    /**
     * The masked projection of a report's location fields.
     *
     * @param coordinatesApproximate true when the coordinates have been coarsened, so a client can
     *                               render an uncertainty circle instead of a false-precision pin
     * @param galleryVisible         false when the media collection must be rendered as empty
     */
    public record LocationView(
            BigDecimal incidentLatitude,
            BigDecimal incidentLongitude,
            boolean coordinatesApproximate,
            String pickupPlace,
            BigDecimal pickupLatitude,
            BigDecimal pickupLongitude,
            String pickupInstructions,
            String identifyingDetail,
            boolean galleryVisible) {
    }
}
