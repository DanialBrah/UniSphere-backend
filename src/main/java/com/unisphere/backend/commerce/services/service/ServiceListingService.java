package com.unisphere.backend.commerce.services.service;

import com.unisphere.backend.commerce.services.dto.request.CreateServiceListingRequest;
import com.unisphere.backend.commerce.services.dto.request.ServiceListingStatusUpdateRequest;
import com.unisphere.backend.commerce.services.dto.request.UpdateServiceListingRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceListingResponse;
import com.unisphere.backend.commerce.services.dto.response.ServiceListingStatsResponse;
import com.unisphere.backend.commerce.services.dto.response.ServiceListingSummaryResponse;
import com.unisphere.backend.commerce.services.dto.response.ServiceProviderResponse;
import com.unisphere.backend.commerce.services.entity.ServiceListing;
import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
import com.unisphere.backend.commerce.services.repository.ServiceOrderRepository;
import com.unisphere.backend.commerce.services.repository.ServiceListingRepository;
import com.unisphere.backend.common.exception.InvalidServiceListingStatusTransitionException;
import com.unisphere.backend.common.exception.ServiceListingNotFoundException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.messaging.dto.request.CreateConversationRequest;
import com.unisphere.backend.social.messaging.dto.response.ConversationResponse;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import com.unisphere.backend.social.messaging.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Listing CRUD, the browse feed, search, per-listing stats, {@code inquire}, and every response
 * mapping in the module. The private {@code to*} methods at the bottom are the only places a
 * {@link ServiceListing} is turned into a DTO. Mirrors {@code JobService}'s shape.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ServiceListingService {

    private final ServiceListingRepository listingRepository;
    private final ServiceOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final ServiceAccessService accessService;
    private final ServiceMediaService mediaService;
    private final ConversationService conversationService;

    // ── Writes ───────────────────────────────────────────────────────────────

    public ServiceListingResponse createListing(CreateServiceListingRequest req, User currentUser) {
        accessService.assertCanCreate(currentUser);

        ServicePricingType pricingType = req.pricingType() != null ? req.pricingType() : ServicePricingType.FIXED;
        validatePricingRules(pricingType, req.price());

        if (req.portfolioImageKey() != null) {
            mediaService.assertOwnedKey(req.portfolioImageKey(), currentUser);
        }

        ServiceListing listing = new ServiceListing();
        listing.setProviderId(currentUser.getId());
        listing.setUniversityId(accessService.viewerUniversityId(currentUser));
        listing.setTitle(req.title());
        listing.setDescription(req.description());
        listing.setCategory(req.category());
        listing.setPricingType(pricingType);
        listing.setPrice(req.price());
        listing.setDeliveryMode(req.deliveryMode() != null ? req.deliveryMode() : ServiceDeliveryMode.BOTH);
        listing.setPortfolioImageKey(req.portfolioImageKey());
        listing.setStatus(ServiceListingStatus.ACTIVE);

        return toResponse(listingRepository.save(listing), currentUser);
    }

    public ServiceListingResponse updateListing(Long listingId, UpdateServiceListingRequest req, User currentUser) {
        ServiceListing listing = findViewableListing(listingId, currentUser);
        accessService.assertCanModify(listing, currentUser);

        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            listing.setTitle(req.title());
        }
        if (req.description() != null) listing.setDescription(blankToNull(req.description()));
        if (req.category() != null) {
            if (req.category().isBlank()) {
                throw new IllegalArgumentException("category cannot be blank");
            }
            listing.setCategory(req.category());
        }
        if (req.pricingType() != null) listing.setPricingType(req.pricingType());
        if (req.deliveryMode() != null) listing.setDeliveryMode(req.deliveryMode());

        applyPrice(listing, req);
        applyPortfolioImageKey(listing, req, currentUser);

        // Re-check the cross-field invariant against the full post-update state, not just the
        // fields that changed this call — mirrors JobService.updateJob.
        validatePricingRules(listing.getPricingType(), listing.getPrice());

        return toResponse(listingRepository.save(listing), currentUser);
    }

    /** The listing availability toggle, in one place. */
    public ServiceListingResponse changeStatus(Long listingId, ServiceListingStatusUpdateRequest req, User currentUser) {
        ServiceListing listing = findViewableListing(listingId, currentUser);
        accessService.assertCanModify(listing, currentUser);

        ServiceListingStatus from = listing.getStatus();
        ServiceListingStatus to = req.status();
        if (from == to) {
            throw new InvalidServiceListingStatusTransitionException(from, to);
        }

        listing.setStatus(to);
        return toResponse(listingRepository.save(listing), currentUser);
    }

    /** Soft delete. Blocked while the listing has any non-terminal order. */
    public void deleteListing(Long listingId, User currentUser) {
        ServiceListing listing = findViewableListing(listingId, currentUser);
        accessService.assertCanModify(listing, currentUser);
        if (orderRepository.existsByListingIdAndStatusIn(listingId, NON_TERMINAL_ORDER_STATUSES)) {
            throw new IllegalArgumentException(
                    "This listing has orders in progress — resolve them before deleting it");
        }
        listing.setDeletedAt(java.time.LocalDateTime.now());
        listingRepository.save(listing);
    }

    /**
     * Find-or-create a DIRECT conversation with the listing's provider — the lightweight "message
     * the provider" flow, independent of placing a formal order. Delegates straight to the existing
     * messaging module; no new entity, no new chat infrastructure.
     */
    public ConversationResponse inquire(Long listingId, User currentUser) {
        ServiceListing listing = findViewableListing(listingId, currentUser);
        if (listing.getProviderId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You cannot message yourself about your own listing");
        }
        return conversationService.createConversation(
                new CreateConversationRequest(ConversationType.DIRECT, List.of(listing.getProviderId()), null),
                currentUser);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ServiceListingResponse getListingById(Long listingId, User currentUser) {
        return toResponse(findViewableListing(listingId, currentUser), currentUser);
    }

    /**
     * The browse feed. A client-supplied {@code PAUSED} filter is rejected outright — paused
     * listings are only visible via {@code /services/me} — and a null filter defaults to
     * {@code ACTIVE}.
     */
    @Transactional(readOnly = true)
    public Page<ServiceListingSummaryResponse> getFeed(String category, ServicePricingType pricingType,
                                                        ServiceDeliveryMode deliveryMode, Long universityId,
                                                        ServiceListingStatus status, Pageable pageable, User currentUser) {
        if (status == ServiceListingStatus.PAUSED) {
            throw new IllegalArgumentException("Paused listings are only visible via /services/me");
        }
        ServiceListingStatus effectiveStatus = status != null ? status : ServiceListingStatus.ACTIVE;
        return toSummaryResponses(
                listingRepository.findFeed(isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                        effectiveStatus, category, pricingType, deliveryMode, universityId, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ServiceListingSummaryResponse> search(String query, Pageable pageable, User currentUser) {
        String sanitized = sanitizeBooleanQuery(query);
        if (sanitized.isEmpty()) {
            return Page.empty(pageable);
        }
        return toSummaryResponses(
                listingRepository.searchFullText(sanitized, isAdmin(currentUser),
                        accessService.viewerUniversityId(currentUser), stripSort(pageable)),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ServiceListingSummaryResponse> getMyListings(ServiceListingStatus status, Pageable pageable, User currentUser) {
        return toSummaryResponses(listingRepository.findByProviderId(currentUser.getId(), status, pageable), currentUser);
    }

    @Transactional(readOnly = true)
    public ServiceListingStatsResponse getStats(Long listingId, User currentUser) {
        ServiceListing listing = findActiveListing(listingId);
        accessService.assertCanModify(listing, currentUser);

        Map<ServiceOrderStatus, Long> byStatus = zeroFilled(ServiceOrderStatus.class);
        orderRepository.countByListingIdGroupByStatus(listingId)
                .forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));

        int total = byStatus.values().stream().mapToInt(Long::intValue).sum();
        return new ServiceListingStatsResponse(
                byStatus.get(ServiceOrderStatus.PENDING),
                byStatus.get(ServiceOrderStatus.ACCEPTED),
                byStatus.get(ServiceOrderStatus.IN_PROGRESS),
                byStatus.get(ServiceOrderStatus.COMPLETED),
                byStatus.get(ServiceOrderStatus.CANCELLED),
                byStatus.get(ServiceOrderStatus.DISPUTED),
                total);
    }

    // ── Shared with ServiceOrderService / ServiceReviewService ─────────────────

    private static final Set<ServiceOrderStatus> NON_TERMINAL_ORDER_STATUSES = Set.of(
            ServiceOrderStatus.PENDING, ServiceOrderStatus.ACCEPTED, ServiceOrderStatus.IN_PROGRESS);

    ServiceListing findActiveListing(Long listingId) {
        return listingRepository.findActiveById(listingId)
                .orElseThrow(() -> new ServiceListingNotFoundException(listingId));
    }

    /** Tolerant lookup for read paths that must still work after the parent listing is gone (soft-deleted). */
    java.util.Optional<ServiceListing> findListingIfPresent(Long listingId) {
        return listingRepository.findActiveById(listingId);
    }

    /** Batch lookup for a page of orders spanning multiple listings — one query, not one per row. */
    Map<Long, ServiceListing> loadListingsById(java.util.Collection<Long> listingIds) {
        if (listingIds.isEmpty()) return Map.of();
        return listingRepository.findAllById(listingIds).stream()
                .collect(Collectors.toMap(ServiceListing::getId, l -> l));
    }

    /**
     * Masks a PAUSED listing as 404 for anyone but its provider/ADMIN, so the row's existence never
     * leaks through a 403 — same nuance {@code JobService.findViewableJob} applies to DRAFT jobs.
     */
    ServiceListing findViewableListing(Long listingId, User viewer) {
        ServiceListing listing = findActiveListing(listingId);
        if (listing.getStatus() == ServiceListingStatus.PAUSED && !accessService.isOwnerOrAdmin(listing, viewer)) {
            throw new ServiceListingNotFoundException(listingId);
        }
        return listing;
    }

    ServiceListingResponse toResponse(ServiceListing listing, User currentUser) {
        PageContext ctx = loadPageContext(List.of(listing), currentUser);
        return buildResponse(listing, ctx);
    }

    Map<Long, ServiceProviderResponse> resolveProviders(java.util.Collection<Long> providerIds) {
        return loadUsers(providerIds).values().stream()
                .collect(Collectors.toMap(User::getId, this::toProviderResponse));
    }

    ServiceProviderResponse toProviderResponse(User user) {
        if (user == null) return null;
        return new ServiceProviderResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), user.getRole().name());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private org.springframework.data.domain.PageRequest stripSort(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private String sanitizeBooleanQuery(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[+\\-><()~*\"@]", " ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) map.put(constant, 0L);
        return map;
    }

    /** FIXED/HOURLY require a price; NEGOTIABLE forbids one — enforced app-side, no DB CHECK. */
    private void validatePricingRules(ServicePricingType pricingType, BigDecimal price) {
        if (pricingType == ServicePricingType.NEGOTIABLE) {
            if (price != null) {
                throw new IllegalArgumentException("price must not be set when pricingType is NEGOTIABLE");
            }
        } else if (price == null) {
            throw new IllegalArgumentException("price is required when pricingType is FIXED or HOURLY");
        }
    }

    private void applyPrice(ServiceListing listing, UpdateServiceListingRequest req) {
        if (req.price() != null) {
            listing.setPrice(req.price());
        } else if (Boolean.TRUE.equals(req.clearPrice())) {
            listing.setPrice(null);
        }
    }

    private void applyPortfolioImageKey(ServiceListing listing, UpdateServiceListingRequest req, User currentUser) {
        if (req.portfolioImageKey() != null) {
            mediaService.assertOwnedKey(req.portfolioImageKey(), currentUser);
            listing.setPortfolioImageKey(req.portfolioImageKey());
        } else if (Boolean.TRUE.equals(req.clearPortfolioImageKey())) {
            listing.setPortfolioImageKey(null);
        }
    }

    private Map<Long, User> loadUsers(java.util.Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    private Page<ServiceListingSummaryResponse> toSummaryResponses(Page<ServiceListing> listings, User currentUser) {
        PageContext ctx = loadPageContext(listings.getContent(), currentUser);
        return listings.map(l -> toSummaryResponse(l, ctx));
    }

    /**
     * Everything a page of listings needs that isn't on the rows themselves, loaded in one query
     * regardless of page size: the provider block.
     */
    private record PageContext(User viewer, Map<Long, User> providers) {}

    private PageContext loadPageContext(List<ServiceListing> listings, User currentUser) {
        if (listings.isEmpty()) return new PageContext(currentUser, Map.of());
        Set<Long> providerIds = listings.stream().map(ServiceListing::getProviderId).collect(Collectors.toSet());
        return new PageContext(currentUser, loadUsers(providerIds));
    }

    private ServiceListingResponse buildResponse(ServiceListing listing, PageContext ctx) {
        return new ServiceListingResponse(
                listing.getId(),
                toProviderResponse(ctx.providers().get(listing.getProviderId())),
                listing.getTitle(), listing.getDescription(), listing.getCategory(),
                listing.getPricingType(), listing.getPrice(), listing.getDeliveryMode(),
                mediaUrlResolver.toViewableUrl(listing.getPortfolioImageKey()),
                listing.getRatingAvg(), listing.getRatingCount(), listing.getStatus(),
                listing.getUniversityId(),
                accessService.isOwnerOrAdmin(listing, ctx.viewer()),
                listing.getCreatedAt(), listing.getUpdatedAt());
    }

    private ServiceListingSummaryResponse toSummaryResponse(ServiceListing listing, PageContext ctx) {
        return new ServiceListingSummaryResponse(
                listing.getId(),
                toProviderResponse(ctx.providers().get(listing.getProviderId())),
                listing.getTitle(), listing.getCategory(),
                listing.getPricingType(), listing.getPrice(), listing.getDeliveryMode(),
                mediaUrlResolver.toViewableUrl(listing.getPortfolioImageKey()),
                listing.getRatingAvg(), listing.getRatingCount(), listing.getStatus(),
                listing.getUniversityId(),
                accessService.isOwnerOrAdmin(listing, ctx.viewer()),
                listing.getCreatedAt());
    }
}
