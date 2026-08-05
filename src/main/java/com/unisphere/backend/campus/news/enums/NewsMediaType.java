package com.unisphere.backend.campus.news.enums;

/**
 * Duplicates social.posting.enums.MediaType by design: campus/ currently depends only on common,
 * config and identity, and importing the posting enum would be the first campus -> social edge
 * for four lines. Each table declares its own MySQL ENUM('IMAGE','VIDEO') either way, so nothing
 * is shared at the database layer. Promote both to common/storage when a third module needs it.
 */
public enum NewsMediaType {
    IMAGE,
    VIDEO
}
