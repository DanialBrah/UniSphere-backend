package com.unisphere.backend.campus.lostfound.enums;

/**
 * Item category facet.
 *
 * <p>Persisted as a native MySQL {@code ENUM}, so the constant list is part of the schema —
 * adding a member later needs a Liquibase {@code MODIFY COLUMN} changeset.
 *
 * <p>{@code OTHER} is the default rather than a required choice: a report filed in a hurry
 * shouldn't be blocked by a taxonomy question. The match scorer treats {@code OTHER} as a partial
 * category match on either side, so an uncategorised report still surfaces its counterpart.
 */
public enum LostFoundCategory {
    ELECTRONICS,
    DOCUMENTS,
    CARDS_AND_KEYS,
    CLOTHING,
    BAGS,
    ACCESSORIES,
    BOOKS,
    SPORTS,
    PETS,
    OTHER
}
