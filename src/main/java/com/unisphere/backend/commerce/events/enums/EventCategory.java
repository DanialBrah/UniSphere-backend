package com.unisphere.backend.commerce.events.enums;

/**
 * Event category facet.
 *
 * <p>Persisted as a native MySQL {@code ENUM}, so the constant list is part of the schema — adding
 * a member later needs a Liquibase {@code MODIFY COLUMN} changeset.
 *
 * <p>{@code OTHER} is the default rather than a required choice, same as {@code LostFoundCategory}.
 */
public enum EventCategory {
    ACADEMIC,
    CAREER,
    WORKSHOP,
    SOCIAL,
    SPORTS,
    CULTURAL,
    TECH,
    CLUB,
    ORIENTATION,
    OTHER
}
