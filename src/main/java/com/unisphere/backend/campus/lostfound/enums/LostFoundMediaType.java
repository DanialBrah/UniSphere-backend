package com.unisphere.backend.campus.lostfound.enums;

/**
 * Duplicates {@code campus.news.enums.NewsMediaType} and {@code social.posting.enums.MediaType} by
 * design — same call those two already made. Each table declares its own {@code ENUM('IMAGE','VIDEO')}
 * column anyway, so sharing one Java enum across module boundaries would couple three modules for
 * two constants.
 */
public enum LostFoundMediaType {
    IMAGE,
    VIDEO
}
