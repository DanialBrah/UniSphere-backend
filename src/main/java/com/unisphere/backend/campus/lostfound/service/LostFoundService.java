package com.unisphere.backend.campus.lostfound.service;

import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundMediaItem;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundStatusUpdateRequest;
import com.unisphere.backend.campus.lostfound.dto.request.UpdateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.dto.response.*;
import com.unisphere.backend.campus.lostfound.entity.LostFoundClaim;
import com.unisphere.backend.campus.lostfound.entity.LostFoundItem;
import com.unisphere.backend.campus.lostfound.entity.LostFoundItemMedia;
import com.unisphere.backend.campus.lostfound.enums.*;
import com.unisphere.backend.campus.lostfound.mapper.LostFoundMapper;
import com.unisphere.backend.campus.lostfound.repository.LostFoundClaimRepository;
import com.unisphere.backend.campus.lostfound.repository.LostFoundItemRepository;
import com.unisphere.backend.campus.lostfound.service.LostFoundAccessService.LocationView;
import com.unisphere.backend.common.exception.InvalidLostFoundStatusTransitionException;
import com.unisphere.backend.common.exception.LostFoundItemNotFoundException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Item CRUD, the board feed, search, the two geo queries, stats, and every response mapping in the
 * module.
 *
 * <p>The three private {@code to*Response} methods at the bottom are the only places a
 * {@link LostFoundItem} is turned into a DTO anywhere in the codebase — the privacy guard hangs off
 * them. {@code LostFoundClaimService} and {@code LostFoundMatchService} both delegate here rather
 * than mapping themselves.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LostFoundService {

    /**
     * FULLTEXT BOOLEAN MODE treats these as operators; an unbalanced one raises a MySQL syntax
     * error, which would surface as a 500 on a plain user search.
     */
    private static final Pattern FULLTEXT_OPERATORS = Pattern.compile("[+\\-><()~*\"@]");

    /** Kilometres per degree of latitude. Longitude shrinks by cos(latitude); see bbox maths below. */
    private static final double KM_PER_DEGREE = 111.045;

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final LostFoundItemRepository lostFoundItemRepository;
    private final LostFoundClaimRepository lostFoundClaimRepository;
    private final UserRepository userRepository;
    private final LostFoundMapper lostFoundMapper;
    private final MediaUrlResolver mediaUrlResolver;
    private final LostFoundAccessService accessService;
    private final LostFoundMediaService lostFoundMediaService;

    @Value("${lost-found.nearby-default-radius-km:5}")
    private double defaultRadiusKm;

    @Value("${lost-found.nearby-max-radius-km:50}")
    private double maxRadiusKm;

    @Value("${lost-found.map-max-pins:500}")
    private int mapMaxPins;

    // ── Writes ───────────────────────────────────────────────────────────────

    public LostFoundItemResponse createItem(CreateLostFoundItemRequest req, User currentUser) {
        validateCoordinatePair(req.incidentLatitude(), req.incidentLongitude(), "incident");
        validateCoordinatePair(req.pickupLatitude(), req.pickupLongitude(), "pickup");
        validatePickupPresence(req.itemType(), req.pickupPlace(), req.pickupLatitude());

        LostFoundItem item = new LostFoundItem();
        item.setReportedBy(currentUser.getId());
        // Derived from the reporter, never from the request — see LostFoundAccessService.
        item.setUniversityId(accessService.resolveItemUniversityId(currentUser));
        item.setItemType(req.itemType());
        item.setCategory(req.category() != null ? req.category() : LostFoundCategory.OTHER);
        item.setStatus(LostFoundItemStatus.OPEN);
        item.setTitle(req.title());
        item.setDescription(req.description());
        item.setIdentifyingDetail(req.identifyingDetail());
        item.setIncidentPlace(req.incidentPlace());
        item.setIncidentLatitude(req.incidentLatitude());
        item.setIncidentLongitude(req.incidentLongitude());
        item.setPickupPlace(req.pickupPlace());
        item.setPickupLatitude(req.pickupLatitude());
        item.setPickupLongitude(req.pickupLongitude());
        item.setPickupInstructions(req.pickupInstructions());
        item.setOccurredAt(req.occurredAt());

        if (req.primaryImageKey() != null && !req.primaryImageKey().isBlank()) {
            lostFoundMediaService.assertOwnedKey(req.primaryImageKey(), currentUser);
            item.setPrimaryImageKey(req.primaryImageKey());
        }
        appendMedia(item, req.media(), currentUser);

        return toResponse(lostFoundItemRepository.save(item), currentUser);
    }

    public LostFoundItemResponse updateItem(Long itemId, UpdateLostFoundItemRequest req, User currentUser) {
        LostFoundItem item = findActiveItem(itemId);
        accessService.assertCanModify(item, currentUser);

        if (req.category() != null) item.setCategory(req.category());
        if (req.title() != null) item.setTitle(req.title());
        if (req.description() != null) item.setDescription(blankToNull(req.description()));
        if (req.identifyingDetail() != null) item.setIdentifyingDetail(blankToNull(req.identifyingDetail()));
        if (req.incidentPlace() != null) item.setIncidentPlace(blankToNull(req.incidentPlace()));
        if (req.pickupInstructions() != null) item.setPickupInstructions(blankToNull(req.pickupInstructions()));
        if (req.occurredAt() != null) item.setOccurredAt(req.occurredAt());

        if (req.primaryImageKey() != null) {
            if (req.primaryImageKey().isBlank()) {
                item.setPrimaryImageKey(null);
            } else {
                lostFoundMediaService.assertOwnedKey(req.primaryImageKey(), currentUser);
                item.setPrimaryImageKey(req.primaryImageKey());
            }
        }

        applyIncidentLocation(item, req);
        applyPickupLocation(item, req);

        // Re-check the FOUND invariant against the post-update state: an update that clears the
        // pickup location would otherwise leave a found item with nowhere to collect it from.
        validatePickupPresence(item.getItemType(), item.getPickupPlace(), item.getPickupLatitude());

        if (req.removeMediaIds() != null && !req.removeMediaIds().isEmpty()) {
            Set<Long> doomed = new HashSet<>(req.removeMediaIds());
            item.getMedia().removeIf(m -> doomed.contains(m.getId()));
        }
        appendMedia(item, req.addMedia(), currentUser);
        if (item.getMedia().size() > 10) {
            throw new IllegalArgumentException("An item may have at most 10 media files");
        }

        return toResponse(lostFoundItemRepository.save(item), currentUser);
    }

    /**
     * The item lifecycle, in one place.
     *
     * <p>CLAIMED and EXPIRED are rejected outright. CLAIMED is written only by
     * {@code LostFoundClaimService} on approval — letting a reporter stamp it by hand would produce
     * a CLAIMED item with no claim row behind it, and the privacy guard's approved-claimant set
     * would then disagree with the item's own status. EXPIRED is written only by the scheduled
     * sweep: expiry is a policy outcome, not a user intent.
     */
    public LostFoundItemResponse changeStatus(Long itemId, LostFoundStatusUpdateRequest req, User currentUser) {
        LostFoundItem item = findActiveItem(itemId);
        accessService.assertCanModify(item, currentUser);

        LostFoundItemStatus from = item.getStatus();
        LostFoundItemStatus to = req.status();

        if (to == LostFoundItemStatus.CLAIMED) {
            throw new InvalidLostFoundStatusTransitionException(
                    "CLAIMED is set by approving a claim, not directly");
        }
        if (to == LostFoundItemStatus.EXPIRED) {
            throw new InvalidLostFoundStatusTransitionException(
                    "EXPIRED is set by the automatic expiry sweep, not directly");
        }
        if (from == to) {
            throw new InvalidLostFoundStatusTransitionException(from, to);
        }

        boolean allowed = switch (from) {
            case OPEN, CLAIMED -> to == LostFoundItemStatus.RESOLVED || to == LostFoundItemStatus.CANCELLED
                    // CLAIMED -> OPEN: the handover fell through. The approved claim row stays as history.
                    || (from == LostFoundItemStatus.CLAIMED && to == LostFoundItemStatus.OPEN);
            case EXPIRED -> to == LostFoundItemStatus.OPEN;
            case RESOLVED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new InvalidLostFoundStatusTransitionException(from, to);
        }

        item.setStatus(to);
        item.setResolvedAt(to == LostFoundItemStatus.RESOLVED ? LocalDateTime.now() : null);
        return toResponse(lostFoundItemRepository.save(item), currentUser);
    }

    public void deleteItem(Long itemId, User currentUser) {
        LostFoundItem item = findActiveItem(itemId);
        accessService.assertCanModify(item, currentUser);
        item.setDeletedAt(LocalDateTime.now());
        lostFoundItemRepository.save(item);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LostFoundItemResponse getItemById(Long itemId, User currentUser) {
        return toResponse(findActiveItem(itemId), currentUser);
    }

    @Transactional(readOnly = true)
    public Page<LostFoundItemSummaryResponse> getFeed(LostFoundItemType type,
                                                      LostFoundItemStatus status,
                                                      LostFoundCategory category,
                                                      Pageable pageable,
                                                      User currentUser) {
        return toSummaryResponses(
                lostFoundItemRepository.findFeed(isAdmin(currentUser),
                        accessService.viewerUniversityId(currentUser), type, status, category, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<LostFoundItemSummaryResponse> search(String query,
                                                     LostFoundItemType type,
                                                     LostFoundItemStatus status,
                                                     Pageable pageable,
                                                     User currentUser) {
        String sanitized = sanitizeBooleanQuery(query);
        if (sanitized.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return toSummaryResponses(
                lostFoundItemRepository.searchFullText(sanitized, isAdmin(currentUser),
                        accessService.viewerUniversityId(currentUser),
                        nameOf(type), nameOf(status), stripSort(pageable)),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<LostFoundItemSummaryResponse> getMyItems(LostFoundItemStatus status,
                                                         Pageable pageable,
                                                         User currentUser) {
        return toSummaryResponses(
                lostFoundItemRepository.findByReporter(currentUser.getId(), status, pageable),
                currentUser);
    }

    /**
     * Radius search. The bounding box is computed here, in Java, rather than in SQL: a per-row
     * expression on the latitude column would defeat {@code idx_lfitem_geo} entirely.
     */
    @Transactional(readOnly = true)
    public Page<LostFoundNearbyItemResponse> getNearby(BigDecimal lat, BigDecimal lng, Double radiusKm,
                                                       LostFoundItemType type,
                                                       LostFoundItemStatus status,
                                                       LostFoundCategory category,
                                                       Pageable pageable,
                                                       User currentUser) {
        requireCoordinate(lat, -90, 90, "lat");
        requireCoordinate(lng, -180, 180, "lng");

        double radius = radiusKm != null ? radiusKm : defaultRadiusKm;
        // Clamped rather than rejected: an over-wide radius is a UI slider at its end stop, not an
        // error, and the cap is what bounds how much of the geo index one request can scan.
        radius = Math.min(Math.max(radius, 0.1), maxRadiusKm);

        double latDelta = radius / KM_PER_DEGREE;
        // Longitude degrees shrink towards the poles. The 0.01 floor on cos() stops the delta
        // diverging to infinity near the poles — it caps the span at the whole globe instead.
        double lngDelta = radius / (KM_PER_DEGREE
                * Math.max(Math.cos(Math.toRadians(lat.doubleValue())), 0.01));

        Page<LostFoundItem> page = lostFoundItemRepository.findNearby(
                lat, lng,
                lat.subtract(BigDecimal.valueOf(latDelta)), lat.add(BigDecimal.valueOf(latDelta)),
                lng.subtract(BigDecimal.valueOf(lngDelta)), lng.add(BigDecimal.valueOf(lngDelta)),
                radius,
                isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                nameOf(type), nameOf(status), nameOf(category),
                stripSort(pageable));

        PageContext ctx = loadPageContext(page.getContent(), currentUser);
        return page.map(item -> new LostFoundNearbyItemResponse(
                toSummaryResponse(item, ctx),
                haversineKm(lat, lng, item.getIncidentLatitude(), item.getIncidentLongitude())));
    }

    /**
     * Map viewport. Returns a hard-capped list rather than a Page — a map client re-queries on pan
     * instead of paginating, and the count query would be pure waste.
     */
    @Transactional(readOnly = true)
    public List<LostFoundMapPinResponse> getMapPins(BigDecimal minLat, BigDecimal maxLat,
                                                    BigDecimal minLng, BigDecimal maxLng,
                                                    LostFoundItemType type,
                                                    LostFoundItemStatus status,
                                                    User currentUser) {
        requireCoordinate(minLat, -90, 90, "minLat");
        requireCoordinate(maxLat, -90, 90, "maxLat");
        requireCoordinate(minLng, -180, 180, "minLng");
        requireCoordinate(maxLng, -180, 180, "maxLng");
        if (minLat.compareTo(maxLat) >= 0) {
            throw new IllegalArgumentException("minLat must be less than maxLat");
        }
        // An antimeridian-crossing viewport needs two boxes; rejecting it is honest, whereas a
        // single BETWEEN would silently return nothing and look like "no items here".
        if (minLng.compareTo(maxLng) >= 0) {
            throw new IllegalArgumentException(
                    "minLng must be less than maxLng (viewports crossing the antimeridian are not supported)");
        }

        List<LostFoundItemRepository.MapPinView> pins = lostFoundItemRepository.findInViewport(
                minLat, maxLat, minLng, maxLng,
                isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                type, status,
                PageRequest.of(0, mapMaxPins));
        if (pins.isEmpty()) return List.of();

        // One query for the whole viewport, not one per pin — a 500-pin viewport would otherwise
        // issue 500 claim lookups.
        Set<Long> pinIds = pins.stream()
                .map(LostFoundItemRepository.MapPinView::getId)
                .collect(Collectors.toSet());
        Set<Long> approvedItemIds = lostFoundClaimRepository
                .findByItemIdInAndClaimantIdAndStatusIn(pinIds, currentUser.getId(),
                        List.of(LostFoundClaimStatus.APPROVED)).stream()
                .map(LostFoundClaim::getItemId)
                .collect(Collectors.toSet());

        return pins.stream().map(pin -> toPin(pin, currentUser, approvedItemIds)).toList();
    }

    @Transactional(readOnly = true)
    public LostFoundStatsResponse getStats(User currentUser) {
        boolean isAdmin = isAdmin(currentUser);
        Long universityId = accessService.viewerUniversityId(currentUser);

        Map<LostFoundItemType, Long> byType = zeroFilled(LostFoundItemType.class);
        lostFoundItemRepository.countByType(isAdmin, universityId)
                .forEach(row -> byType.put(row.getItemType(), row.getTotal()));

        Map<LostFoundItemStatus, Long> byStatus = zeroFilled(LostFoundItemStatus.class);
        lostFoundItemRepository.countByStatus(isAdmin, universityId)
                .forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));

        Map<LostFoundCategory, Long> byCategory = zeroFilled(LostFoundCategory.class);
        lostFoundItemRepository.countByCategory(isAdmin, universityId)
                .forEach(row -> byCategory.put(row.getCategory(), row.getTotal()));

        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new LostFoundStatsResponse(total, byType, byStatus, byCategory,
                lostFoundClaimRepository.countByStatus(LostFoundClaimStatus.PENDING));
    }

    // ── Shared with the claim and match services ─────────────────────────────

    LostFoundItem findActiveItem(Long itemId) {
        return lostFoundItemRepository.findActiveById(itemId)
                .orElseThrow(() -> new LostFoundItemNotFoundException(itemId));
    }

    /**
     * Entry point for {@code LostFoundMatchService} — it hands its scored entity list here rather
     * than building DTOs itself, so the privacy mask applies to match suggestions too.
     */
    List<LostFoundItemSummaryResponse> toSummaryResponses(List<LostFoundItem> items, User currentUser) {
        PageContext ctx = loadPageContext(items, currentUser);
        return items.stream().map(i -> toSummaryResponse(i, ctx)).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    /**
     * Native queries carry their own ORDER BY, and Spring appends a Pageable sort as a raw property
     * name — {@code ?sort=createdAt} would emit invalid SQL against the {@code created_at} column.
     */
    private Pageable stripSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * Hibernate binds a Java enum to a <em>native</em> query parameter by its ordinal, which would
     * silently filter on 1 instead of 'FOUND'. Native callers pass the name instead.
     */
    private String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String sanitizeBooleanQuery(String raw) {
        if (raw == null) return "";
        return FULLTEXT_OPERATORS.matcher(raw).replaceAll(" ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) map.put(constant, 0L);
        return map;
    }

    private void requireCoordinate(BigDecimal value, double min, double max, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.doubleValue() < min || value.doubleValue() > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }

    private void validateCoordinatePair(BigDecimal lat, BigDecimal lng, String which) {
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException(
                    which + "Latitude and " + which + "Longitude must be provided together");
        }
    }

    /** A found item nobody can collect is not a useful report. */
    private void validatePickupPresence(LostFoundItemType type, String pickupPlace, BigDecimal pickupLatitude) {
        if (type == LostFoundItemType.FOUND
                && (pickupPlace == null || pickupPlace.isBlank())
                && pickupLatitude == null) {
            throw new IllegalArgumentException(
                    "A FOUND item needs a pickup location — set pickupPlace or pickup coordinates");
        }
    }

    private void applyIncidentLocation(LostFoundItem item, UpdateLostFoundItemRequest req) {
        if (req.incidentLatitude() != null || req.incidentLongitude() != null) {
            validateCoordinatePair(req.incidentLatitude(), req.incidentLongitude(), "incident");
            item.setIncidentLatitude(req.incidentLatitude());
            item.setIncidentLongitude(req.incidentLongitude());
        } else if (Boolean.TRUE.equals(req.clearIncidentLocation())) {
            item.setIncidentLatitude(null);
            item.setIncidentLongitude(null);
        }
    }

    private void applyPickupLocation(LostFoundItem item, UpdateLostFoundItemRequest req) {
        if (req.pickupLatitude() != null || req.pickupLongitude() != null) {
            validateCoordinatePair(req.pickupLatitude(), req.pickupLongitude(), "pickup");
            item.setPickupLatitude(req.pickupLatitude());
            item.setPickupLongitude(req.pickupLongitude());
        } else if (Boolean.TRUE.equals(req.clearPickupLocation())) {
            item.setPickupLatitude(null);
            item.setPickupLongitude(null);
        }
        if (req.pickupPlace() != null) item.setPickupPlace(blankToNull(req.pickupPlace()));
    }

    private void appendMedia(LostFoundItem item, List<LostFoundMediaItem> incoming, User currentUser) {
        if (incoming == null || incoming.isEmpty()) return;
        int nextOrder = item.getMedia().size();
        for (LostFoundMediaItem entry : incoming) {
            // Ownership is checked on attach, not only on delete — otherwise a client can embed
            // someone else's key in their own report.
            lostFoundMediaService.assertOwnedKey(entry.mediaKey(), currentUser);
            LostFoundItemMedia media = new LostFoundItemMedia();
            media.setItem(item);
            media.setMediaKey(entry.mediaKey());
            media.setMediaType("VIDEO".equalsIgnoreCase(entry.mediaType())
                    ? LostFoundMediaType.VIDEO : LostFoundMediaType.IMAGE);
            media.setSortOrder(nextOrder++);
            item.getMedia().add(media);
        }
    }

    /** Great-circle distance in km. Null-safe: items without coordinates report 0. */
    private double haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 0d;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
    }

    // ── Response mapping ─────────────────────────────────────────────────────
    // The three to*Response methods below are the module's only DTO construction sites for an item,
    // and each one applies the privacy mask. See LostFoundAccessService.locationViewFor.

    private Page<LostFoundItemSummaryResponse> toSummaryResponses(Page<LostFoundItem> items, User currentUser) {
        PageContext ctx = loadPageContext(items.getContent(), currentUser);
        return items.map(i -> toSummaryResponse(i, ctx));
    }

    /**
     * Everything a page of items needs that isn't on the rows themselves, loaded in three queries
     * regardless of page size. Mapping item-by-item would issue these per row.
     */
    private record PageContext(User viewer,
                               Map<Long, User> reporters,
                               Map<Long, Long> pendingClaimCounts,
                               Set<Long> viewerApprovedClaimItemIds,
                               Map<Long, LostFoundClaimStatus> viewerClaimStatuses) {}

    private PageContext loadPageContext(List<LostFoundItem> items, User currentUser) {
        if (items.isEmpty()) return new PageContext(currentUser, Map.of(), Map.of(), Set.of(), Map.of());

        Set<Long> itemIds = items.stream().map(LostFoundItem::getId).collect(Collectors.toSet());
        Set<Long> reporterIds = items.stream().map(LostFoundItem::getReportedBy).collect(Collectors.toSet());

        Map<Long, Long> pendingCounts = lostFoundClaimRepository
                .countByItemIdsAndStatus(itemIds, LostFoundClaimStatus.PENDING).stream()
                .collect(Collectors.toMap(LostFoundClaimRepository.CountByKey::getId,
                                          LostFoundClaimRepository.CountByKey::getTotal));

        // The APPROVED subset drives the privacy guard, so this must never become a per-row lookup.
        var viewerClaims = lostFoundClaimRepository.findByItemIdInAndClaimantIdAndStatusIn(
                itemIds, currentUser.getId(),
                List.of(LostFoundClaimStatus.APPROVED, LostFoundClaimStatus.PENDING));

        Set<Long> approvedItemIds = viewerClaims.stream()
                .filter(c -> c.getStatus() == LostFoundClaimStatus.APPROVED)
                .map(LostFoundClaim::getItemId)
                .collect(Collectors.toSet());

        // APPROVED wins over PENDING when a claimant re-applied after an earlier decision.
        Map<Long, LostFoundClaimStatus> claimStatuses = new HashMap<>();
        viewerClaims.forEach(c -> claimStatuses.merge(c.getItemId(), c.getStatus(),
                (a, b) -> a == LostFoundClaimStatus.APPROVED || b == LostFoundClaimStatus.APPROVED
                        ? LostFoundClaimStatus.APPROVED : a));

        return new PageContext(
                currentUser,
                userRepository.findAllById(reporterIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u)),
                pendingCounts,
                approvedItemIds,
                claimStatuses);
    }

    LostFoundItemResponse toResponse(LostFoundItem item, User currentUser) {
        PageContext ctx = loadPageContext(List.of(item), currentUser);
        LocationView view = accessService.locationViewFor(
                item, currentUser, ctx.viewerApprovedClaimItemIds().contains(item.getId()));

        // Presigned for every viewer, not just restricted ones — a plain public URL only resolves
        // on GCS (Garage has no anonymous access, B2 is private).
        List<LostFoundMediaResponse> media = view.galleryVisible()
                ? item.getMedia().stream().map(lostFoundMapper::toMediaResponse).toList()
                : List.of();

        return new LostFoundItemResponse(
                item.getId(),
                resolveUser(item.getReportedBy(), ctx.reporters()),
                item.getItemType(), item.getCategory(), item.getStatus(),
                item.getTitle(), item.getDescription(),
                view.identifyingDetail(),
                mediaUrlResolver.toViewableUrl(item.getPrimaryImageKey()),
                item.getIncidentPlace(), view.incidentLatitude(), view.incidentLongitude(),
                view.coordinatesApproximate(),
                view.pickupPlace(), view.pickupLatitude(), view.pickupLongitude(),
                view.pickupInstructions(),
                item.getUniversityId(), item.getOccurredAt(), item.getResolvedAt(),
                ctx.pendingClaimCounts().getOrDefault(item.getId(), 0L),
                ctx.viewerClaimStatuses().get(item.getId()),
                accessService.isOwnerOrAdmin(item, currentUser),
                media,
                item.getCreatedAt(), item.getUpdatedAt());
    }

    private LostFoundItemSummaryResponse toSummaryResponse(LostFoundItem item, PageContext ctx) {
        User viewer = ctx.viewer();
        LocationView view = accessService.locationViewFor(
                item, viewer, ctx.viewerApprovedClaimItemIds().contains(item.getId()));

        return new LostFoundItemSummaryResponse(
                item.getId(),
                resolveUser(item.getReportedBy(), ctx.reporters()),
                item.getItemType(), item.getCategory(), item.getStatus(),
                item.getTitle(),
                mediaUrlResolver.toViewableUrl(item.getPrimaryImageKey()),
                item.getIncidentPlace(), view.incidentLatitude(), view.incidentLongitude(),
                view.coordinatesApproximate(),
                view.pickupPlace(), view.pickupLatitude(), view.pickupLongitude(),
                item.getUniversityId(), item.getOccurredAt(),
                ctx.pendingClaimCounts().getOrDefault(item.getId(), 0L),
                ctx.viewerClaimStatuses().get(item.getId()),
                accessService.isOwnerOrAdmin(item, viewer),
                item.getCreatedAt());
    }

    /**
     * Map pins take the same masking path as everything else. The projection carries no pickup or
     * media fields at all, so only the coordinates need it — but it still routes through
     * locationViewFor rather than rounding inline, so there is exactly one implementation of the
     * rule to audit.
     */
    private LostFoundMapPinResponse toPin(LostFoundItemRepository.MapPinView pin, User currentUser,
                                          Set<Long> approvedClaimItemIds) {
        LostFoundItem probe = new LostFoundItem();
        probe.setId(pin.getId());
        probe.setReportedBy(pin.getReportedBy());
        probe.setItemType(pin.getItemType());
        probe.setIncidentLatitude(pin.getLatitude());
        probe.setIncidentLongitude(pin.getLongitude());

        LocationView view = accessService.locationViewFor(
                probe, currentUser, approvedClaimItemIds.contains(pin.getId()));
        return new LostFoundMapPinResponse(
                pin.getId(), pin.getItemType(), pin.getStatus(), pin.getCategory(), pin.getTitle(),
                view.incidentLatitude(), view.incidentLongitude(), view.coordinatesApproximate(),
                mediaUrlResolver.toViewableUrl(pin.getPrimaryImageKey()));
    }

    private LostFoundUserResponse resolveUser(Long userId, Map<Long, User> users) {
        User user = users.get(userId);
        if (user == null) return new LostFoundUserResponse(userId, "Unknown", null, "UNKNOWN");
        // avatar_url holds a bare key since changeset 012 — it has to be signed to be fetchable.
        return new LostFoundUserResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), user.getRole().name());
    }
}
