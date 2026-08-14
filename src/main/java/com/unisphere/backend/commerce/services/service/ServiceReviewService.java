package com.unisphere.backend.commerce.services.service;

import com.unisphere.backend.commerce.services.dto.request.CreateServiceReviewRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceReviewResponse;
import com.unisphere.backend.commerce.services.entity.ServiceListing;
import com.unisphere.backend.commerce.services.entity.ServiceOrder;
import com.unisphere.backend.commerce.services.entity.ServiceReview;
import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
import com.unisphere.backend.commerce.services.repository.ServiceListingRepository;
import com.unisphere.backend.commerce.services.repository.ServiceReviewRepository;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reviews on a {@code COMPLETED} order — at most one per {@code (order, reviewer)} pair. Only
 * client-authored reviews (of the provider) roll into {@code ServiceListing.ratingAvg}/
 * {@code ratingCount}; provider-authored reviews of the client are stored but not aggregated
 * anywhere this pass — no client-facing reputation surface yet.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ServiceReviewService {

    private final ServiceReviewRepository reviewRepository;
    private final ServiceListingRepository listingRepository;
    private final ServiceListingService listingService;
    private final ServiceOrderService orderService;

    public ServiceReviewResponse createReview(Long orderId, CreateServiceReviewRequest req, User currentUser) {
        ServiceOrder order = orderService.findOrder(orderId);
        if (order.getStatus() != ServiceOrderStatus.COMPLETED) {
            throw new IllegalArgumentException("Reviews can only be left on completed orders");
        }

        ServiceListing listing = listingService.findActiveListing(order.getListingId());

        Long revieweeId;
        if (order.getClientId().equals(currentUser.getId())) {
            revieweeId = listing.getProviderId();
        } else if (listing.getProviderId().equals(currentUser.getId())) {
            revieweeId = order.getClientId();
        } else {
            throw new UnauthorizedActionException("Only the order's client or provider can leave a review");
        }

        if (reviewRepository.existsByOrderIdAndReviewerId(orderId, currentUser.getId())) {
            throw new IllegalArgumentException("You have already reviewed this order");
        }

        ServiceReview review = new ServiceReview();
        review.setOrderId(orderId);
        review.setReviewerId(currentUser.getId());
        review.setRevieweeId(revieweeId);
        review.setRating(req.rating());
        review.setComment(req.comment());
        ServiceReview saved = reviewRepository.save(review);

        if (revieweeId.equals(listing.getProviderId())) {
            recomputeListingRating(listing, revieweeId);
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceReviewResponse> listReviewsForOrder(Long orderId, User currentUser) {
        ServiceOrder order = orderService.findOrder(orderId);
        ServiceListing listing = listingService.findActiveListing(order.getListingId());
        boolean isParty = order.getClientId().equals(currentUser.getId())
                || listing.getProviderId().equals(currentUser.getId())
                || currentUser.getRole() == Role.ADMIN;
        if (!isParty) {
            throw new UnauthorizedActionException("Only the order's client, provider, or an admin can view its reviews");
        }
        return reviewRepository.findByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    /** Public — client-authored reviews of a listing's provider. */
    @Transactional(readOnly = true)
    public Page<ServiceReviewResponse> listReviewsForListing(Long listingId, Pageable pageable, User currentUser) {
        ServiceListing listing = listingService.findViewableListing(listingId, currentUser);
        return reviewRepository.findByListingAndProviderReviewee(listingId, listing.getProviderId(), pageable)
                .map(this::toResponse);
    }

    private void recomputeListingRating(ServiceListing listing, Long providerId) {
        Object[] aggregate = reviewRepository.aggregateRatingForListing(listing.getId(), providerId);
        Double avg = aggregate[0] == null ? 0.0 : ((Number) aggregate[0]).doubleValue();
        long count = aggregate[1] == null ? 0L : ((Number) aggregate[1]).longValue();

        listing.setRatingAvg(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        listing.setRatingCount((int) count);
        listingRepository.save(listing);
    }

    private ServiceReviewResponse toResponse(ServiceReview review) {
        Map<Long, com.unisphere.backend.commerce.services.dto.response.ServiceProviderResponse> reviewers =
                listingService.resolveProviders(Set.of(review.getReviewerId()));
        return new ServiceReviewResponse(
                review.getId(), review.getOrderId(), reviewers.get(review.getReviewerId()),
                review.getRevieweeId(), review.getRating(), review.getComment(), review.getCreatedAt());
    }
}
