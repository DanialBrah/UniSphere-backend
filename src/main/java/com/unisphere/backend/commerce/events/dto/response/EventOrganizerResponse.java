package com.unisphere.backend.commerce.events.dto.response;

/**
 * Denormalised actor block — the organizer of an event. Mirrors {@code LostFoundUserResponse}.
 *
 * <p>{@code avatarUrl} is resolved through {@code MediaUrlResolver}: avatars are stored as bare keys
 * since changeset 012.
 */
public record EventOrganizerResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String role
) {}
