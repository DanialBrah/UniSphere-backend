package com.unisphere.backend.campus.lostfound.dto.response;

/**
 * Denormalised actor block — the reporter of an item or the claimant on a claim.
 *
 * <p>{@code avatarUrl} is resolved through {@code MediaUrlResolver}: avatars are stored as bare
 * keys since changeset 012.
 */
public record LostFoundUserResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String role
) {}
