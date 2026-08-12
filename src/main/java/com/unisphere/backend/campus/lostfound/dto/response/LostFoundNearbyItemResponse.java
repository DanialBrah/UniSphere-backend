package com.unisphere.backend.campus.lostfound.dto.response;

/**
 * A radius-search hit. A wrapper rather than a nullable {@code distanceKm} on the summary itself,
 * which would be null on every endpoint but this one.
 *
 * <p>{@code distanceKm} is the great-circle distance from the query point to the item's
 * <em>incident</em> location. For a FOUND item shown to an unprivileged viewer the coordinates in
 * {@code item} are coarsened, but this distance is computed from the true ones — it is a scalar
 * with no bearing, so it narrows the location far less than a pin would.
 */
public record LostFoundNearbyItemResponse(
        LostFoundItemSummaryResponse item,
        double distanceKm
) {}
