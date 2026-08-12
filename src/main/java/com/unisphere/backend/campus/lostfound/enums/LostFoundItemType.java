package com.unisphere.backend.campus.lostfound.enums;

/**
 * Which side of the board a report sits on.
 *
 * <p>Drives the whole module: {@link LostFoundItemStatus} transitions are identical for both, but
 * the privacy guard only masks {@code FOUND} items (a {@code LOST} reporter wants maximum reach),
 * and the match scorer only ever considers the <em>counterpart</em> type as a candidate.
 */
public enum LostFoundItemType {
    LOST,
    FOUND;

    /** The type a match for this one would be reported under. */
    public LostFoundItemType counterpart() {
        return this == LOST ? FOUND : LOST;
    }
}
