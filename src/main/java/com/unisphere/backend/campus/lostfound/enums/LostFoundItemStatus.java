package com.unisphere.backend.campus.lostfound.enums;

/**
 * Item lifecycle. The reference schema had three members; these five exist because {@code CLOSED}
 * conflated "returned to its owner" with "gave up", and {@code GET /lost-found/stats} has to tell
 * those apart. MySQL stores an {@code ENUM} as an ordinal, so a member added later costs an
 * {@code ALTER} on a live table — all five are defined up front.
 *
 * <p>The transition table lives in {@code LostFoundService.changeStatus}, which owns the whole
 * machine. Two transitions are deliberately unreachable from {@code PATCH /items/{id}/status}:
 *
 * <ul>
 *   <li>{@code -> CLAIMED} is written only by {@code LostFoundClaimService} on claim approval.
 *       Letting a reporter stamp it by hand would produce a CLAIMED item with no claim row behind
 *       it, and the privacy guard's "approved claimant" set would then disagree with the item's
 *       own status.</li>
 *   <li>{@code -> EXPIRED} is written only by {@code LostFoundExpiryScheduler}. Expiry is a policy
 *       outcome, not a user intent; a reporter who is done with a listing wants {@link #RESOLVED}
 *       or {@link #CANCELLED}.</li>
 * </ul>
 */
public enum LostFoundItemStatus {

    /** Live on the board and accepting claims. */
    OPEN,

    /** A claim was approved; the handover has not been confirmed yet. */
    CLAIMED,

    /** Returned to its owner / recovered. Terminal. */
    RESOLVED,

    /** Aged out of the board by the scheduled sweep. Relistable back to {@link #OPEN}. */
    EXPIRED,

    /** Withdrawn by the reporter — gave up, duplicate, posted in error. Terminal. */
    CANCELLED
}
