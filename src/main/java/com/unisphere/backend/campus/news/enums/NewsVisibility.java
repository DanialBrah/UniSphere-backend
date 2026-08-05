package com.unisphere.backend.campus.news.enums;

/**
 * News-local rather than a reuse of PostVisibility, which also carries FRIENDS and PRIVATE.
 * Institutional news has no friends-graph or private semantics, and reusing that type would let
 * Jackson deserialize "FRIENDS" into a value NewsAccessService cannot handle — trading a
 * compile-time exhaustiveness guarantee for a runtime surprise. Two members also keep the access
 * switch exhaustive without a default branch.
 */
public enum NewsVisibility {
    PUBLIC,
    UNIVERSITY
}
