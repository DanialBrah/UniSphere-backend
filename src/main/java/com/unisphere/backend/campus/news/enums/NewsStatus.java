package com.unisphere.backend.campus.news.enums;

/**
 * There is deliberately no SCHEDULED member: a queued article is DRAFT with a non-null
 * scheduled_at, which idx_news_articles_scheduled serves directly. MySQL stores an ENUM as an
 * ordinal, so adding a fourth member later costs an ALTER on a live table — three is enough.
 */
public enum NewsStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
