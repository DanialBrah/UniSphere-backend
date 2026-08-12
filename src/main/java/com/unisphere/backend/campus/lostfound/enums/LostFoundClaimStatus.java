package com.unisphere.backend.campus.lostfound.enums;

/**
 * Claim lifecycle. Only {@link #PENDING} is non-terminal.
 *
 * <p>Who may drive each transition differs, which is why {@code PATCH /lost-found/claims/{id}}
 * branches on the requested status rather than on the caller's role:
 *
 * <ul>
 *   <li>{@code PENDING -> APPROVED | REJECTED} — the item's reporter, or an ADMIN.</li>
 *   <li>{@code PENDING -> CANCELLED} — the claimant only. Withdrawing your own claim is not a
 *       "review", which is also why the table carries an {@code updated_at} that
 *       {@code community_join_requests} does not.</li>
 * </ul>
 *
 * <p>Approving one claim auto-rejects every other {@link #PENDING} claim on the same item — three
 * dangling pending claims on a CLAIMED item is an unreadable state for everyone involved.
 */
public enum LostFoundClaimStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
