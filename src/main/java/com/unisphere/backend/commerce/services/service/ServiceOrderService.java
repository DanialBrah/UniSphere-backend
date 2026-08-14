package com.unisphere.backend.commerce.services.service;

import com.unisphere.backend.commerce.services.dto.request.CreateServiceOrderRequest;
import com.unisphere.backend.commerce.services.dto.request.ServiceOrderStatusUpdateRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceOrderResponse;
import com.unisphere.backend.commerce.services.entity.ServiceListing;
import com.unisphere.backend.commerce.services.entity.ServiceOrder;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
import com.unisphere.backend.commerce.services.repository.ServiceOrderRepository;
import com.unisphere.backend.common.exception.InvalidServiceOrderTransitionException;
import com.unisphere.backend.common.exception.ServiceOrderNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.messaging.dto.request.CreateConversationRequest;
import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import com.unisphere.backend.social.messaging.dto.response.ConversationResponse;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import com.unisphere.backend.social.messaging.enums.MessageType;
import com.unisphere.backend.social.messaging.service.ConversationService;
import com.unisphere.backend.social.messaging.service.MessageService;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The service order lifecycle — the messaging-integration chokepoint. {@link #createOrder} calls
 * the existing messaging module's {@link ConversationService}/{@link MessageService} as black-box
 * beans (find-or-create the DIRECT conversation, drop a {@code SYSTEM} summary message) inside the
 * same transaction as the order write, exactly the atomicity pattern
 * {@code CommunityMembershipService} uses for its own mirrored conversation writes — no new chat
 * infrastructure.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ServiceOrderService {

    private static final Set<ServiceOrderStatus> CANCELLABLE_FROM = Set.of(
            ServiceOrderStatus.PENDING, ServiceOrderStatus.ACCEPTED);
    private static final Set<ServiceOrderStatus> DISPUTABLE_FROM = Set.of(
            ServiceOrderStatus.ACCEPTED, ServiceOrderStatus.IN_PROGRESS);

    private final ServiceOrderRepository orderRepository;
    private final ServiceListingService listingService;
    private final ServiceAccessService accessService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final NotificationService notificationService;

    // ── Writes ───────────────────────────────────────────────────────────────

    public ServiceOrderResponse createOrder(Long listingId, CreateServiceOrderRequest req, User currentUser) {
        ServiceListing listing = listingService.findViewableListing(listingId, currentUser);
        accessService.assertCanOrder(currentUser);
        if (listing.getProviderId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You cannot order your own service listing");
        }
        if (listing.getStatus() != ServiceListingStatus.ACTIVE) {
            throw new IllegalArgumentException("This listing is not currently accepting orders");
        }

        ServiceOrder order = new ServiceOrder();
        order.setListingId(listingId);
        order.setClientId(currentUser.getId());
        order.setRequirements(req.requirements());
        order.setScheduledAt(req.scheduledAt());
        order.setStatus(ServiceOrderStatus.PENDING);
        order.setAgreedPrice(resolveInitialAgreedPrice(listing, req.proposedPrice()));

        ServiceOrder saved = orderRepository.save(order);

        Long conversationId = openOrderConversation(listing, saved, currentUser);
        saved.setConversationId(conversationId);
        saved = orderRepository.save(saved);

        notificationService.createAndPush(listing.getProviderId(), currentUser.getId(),
                NotificationType.SERVICE_ORDER, saved.getId(), "SERVICE_ORDER_RECEIVED");

        return toResponse(saved, listing);
    }

    /**
     * The single entry point for every actor in the {@link ServiceOrderStatus} state machine —
     * client, provider or admin all funnel through here, mirroring
     * {@code JobApplicationService.updateApplicationStatus}'s two-actor split generalized to three.
     */
    public ServiceOrderResponse updateOrderStatus(Long orderId, ServiceOrderStatusUpdateRequest req, User currentUser) {
        ServiceOrder order = findOrder(orderId);
        ServiceListing listing = listingService.findActiveListing(order.getListingId());

        ServiceOrderStatus from = order.getStatus();
        ServiceOrderStatus to = req.status();
        if (from == to) {
            throw new InvalidServiceOrderTransitionException(from, to);
        }

        boolean isClient = order.getClientId().equals(currentUser.getId());
        boolean isProviderOrAdmin = accessService.isOwnerOrAdmin(listing, currentUser);

        switch (to) {
            case ACCEPTED -> {
                if (from != ServiceOrderStatus.PENDING) throw new InvalidServiceOrderTransitionException(from, to);
                if (!isProviderOrAdmin) {
                    throw new UnauthorizedActionException("Only the provider can accept an order");
                }
                BigDecimal price = req.agreedPrice() != null ? req.agreedPrice() : order.getAgreedPrice();
                if (price == null) {
                    throw new IllegalArgumentException(
                            "An agreed price is required before this order can be accepted");
                }
                order.setAgreedPrice(price);
            }
            case IN_PROGRESS -> {
                if (from != ServiceOrderStatus.ACCEPTED) throw new InvalidServiceOrderTransitionException(from, to);
                if (!isProviderOrAdmin) {
                    throw new UnauthorizedActionException("Only the provider can start work on an order");
                }
            }
            case COMPLETED -> {
                if (from != ServiceOrderStatus.IN_PROGRESS) throw new InvalidServiceOrderTransitionException(from, to);
                if (!isProviderOrAdmin) {
                    throw new UnauthorizedActionException("Only the provider can mark an order complete");
                }
            }
            case CANCELLED -> {
                if (!CANCELLABLE_FROM.contains(from)) throw new InvalidServiceOrderTransitionException(from, to);
                if (!isClient && !isProviderOrAdmin) {
                    throw new UnauthorizedActionException(
                            "Only the client, the provider, or an admin can cancel this order");
                }
            }
            case DISPUTED -> {
                if (!DISPUTABLE_FROM.contains(from)) throw new InvalidServiceOrderTransitionException(from, to);
                if (!isClient && !isProviderOrAdmin) {
                    throw new UnauthorizedActionException(
                            "Only the client, the provider, or an admin can dispute this order");
                }
            }
            case PENDING -> throw new InvalidServiceOrderTransitionException(from, to);
        }

        if (req.scheduledAt() != null) order.setScheduledAt(req.scheduledAt());
        if (req.reason() != null) order.setDecisionReason(req.reason());

        ServiceOrder saved = orderRepository.save(order);
        notifyTransition(saved, listing, from, to, currentUser, isClient);

        return toResponse(saved, listing);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ServiceOrderResponse getOrderById(Long orderId, User currentUser) {
        ServiceOrder order = findOrder(orderId);
        Optional<ServiceListing> listingOpt = listingService.findListingIfPresent(order.getListingId());
        assertCanViewOrder(order, listingOpt, currentUser);
        return listingOpt
                .map(listing -> toResponse(order, listing))
                .orElseGet(() -> toResponseWithoutListing(order));
    }

    /** Order roster for one listing — the provider reviewing requests. */
    @Transactional(readOnly = true)
    public Page<ServiceOrderResponse> listOrdersForListing(Long listingId, ServiceOrderStatus status,
                                                            Pageable pageable, User currentUser) {
        ServiceListing listing = listingService.findViewableListing(listingId, currentUser);
        accessService.assertCanModify(listing, currentUser);
        return orderRepository.findByListing(listingId, status, pageable).map(o -> toResponse(o, listing));
    }

    /** "My orders" as a client, across every listing. */
    @Transactional(readOnly = true)
    public Page<ServiceOrderResponse> myOrders(ServiceOrderStatus status, Pageable pageable, User currentUser) {
        Page<ServiceOrder> page = orderRepository.findByClient(currentUser.getId(), status, pageable);
        return toResponses(page);
    }

    /** "My orders received" as a provider, across every listing owned. */
    @Transactional(readOnly = true)
    public Page<ServiceOrderResponse> receivedOrders(ServiceOrderStatus status, Pageable pageable, User currentUser) {
        Page<ServiceOrder> page = orderRepository.findReceivedByProvider(currentUser.getId(), status, pageable);
        return toResponses(page);
    }

    ServiceOrder findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceOrderNotFoundException(orderId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * FIXED/HOURLY always copy the listing's own price — any client-supplied {@code proposedPrice}
     * is ignored. NEGOTIABLE listings have no listing-level price, so whatever the client proposes
     * (possibly null) is used as-is; a null value is resolved later, at accept time, via
     * {@code ServiceOrderStatusUpdateRequest.agreedPrice}.
     */
    private BigDecimal resolveInitialAgreedPrice(ServiceListing listing, BigDecimal proposedPrice) {
        if (listing.getPricingType() != ServicePricingType.NEGOTIABLE) {
            return listing.getPrice();
        }
        return proposedPrice;
    }

    /**
     * Find-or-create the DIRECT conversation with the provider, then drop a {@code SYSTEM} summary
     * message into it — the concrete "reach out via messaging" mechanism for a formal order.
     */
    private Long openOrderConversation(ServiceListing listing, ServiceOrder order, User currentUser) {
        ConversationResponse conversation = conversationService.createConversation(
                new CreateConversationRequest(ConversationType.DIRECT, List.of(listing.getProviderId()), null),
                currentUser);

        String summary = "New service order request for \"" + listing.getTitle()
                + "\" (order #" + order.getId() + ").";
        messageService.sendMessage(
                new SendMessageRequest(conversation.id(), summary, MessageType.SYSTEM, null, null),
                currentUser);

        return conversation.id();
    }

    private void notifyTransition(ServiceOrder order, ServiceListing listing, ServiceOrderStatus from,
                                  ServiceOrderStatus to, User actor, boolean actorIsClient) {
        boolean actorIsProvider = listing.getProviderId().equals(actor.getId());

        // Self-withdraw from PENDING — self-action, no notification, mirrors JobApplicationService.withdraw.
        if (to == ServiceOrderStatus.CANCELLED && from == ServiceOrderStatus.PENDING && actorIsClient) {
            return;
        }

        String targetType = switch (to) {
            case ACCEPTED -> "SERVICE_ORDER_ACCEPTED";
            case IN_PROGRESS -> "SERVICE_ORDER_STARTED";
            case COMPLETED -> "SERVICE_ORDER_COMPLETED";
            case CANCELLED -> from == ServiceOrderStatus.PENDING ? "SERVICE_ORDER_DECLINED" : "SERVICE_ORDER_CANCELLED";
            case DISPUTED -> "SERVICE_ORDER_DISPUTED";
            case PENDING -> "SERVICE_ORDER_UPDATED";
        };

        if (actorIsClient) {
            notificationService.createAndPush(listing.getProviderId(), actor.getId(),
                    NotificationType.SERVICE_ORDER, order.getId(), targetType);
        } else if (actorIsProvider) {
            notificationService.createAndPush(order.getClientId(), actor.getId(),
                    NotificationType.SERVICE_ORDER, order.getId(), targetType);
        } else {
            // Admin acting on behalf of neither party — notify both.
            notificationService.createAndPush(order.getClientId(), actor.getId(),
                    NotificationType.SERVICE_ORDER, order.getId(), targetType);
            notificationService.createAndPush(listing.getProviderId(), actor.getId(),
                    NotificationType.SERVICE_ORDER, order.getId(), targetType);
        }
    }

    private void assertCanViewOrder(ServiceOrder order, Optional<ServiceListing> listingOpt, User currentUser) {
        boolean isClient = order.getClientId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == com.unisphere.backend.identity.entity.Role.ADMIN;
        boolean isProvider = listingOpt.map(l -> l.getProviderId().equals(currentUser.getId())).orElse(false);
        if (!isClient && !isAdmin && !isProvider) {
            throw new UnauthorizedActionException("Only the order's client, provider, or an admin can view it");
        }
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    private Page<ServiceOrderResponse> toResponses(Page<ServiceOrder> orders) {
        Map<Long, ServiceListing> listings = listingService.loadListingsById(
                orders.getContent().stream().map(ServiceOrder::getListingId).collect(Collectors.toSet()));
        return orders.map(o -> {
            ServiceListing listing = listings.get(o.getListingId());
            return listing != null ? toResponse(o, listing) : toResponseWithoutListing(o);
        });
    }

    private ServiceOrderResponse toResponse(ServiceOrder order, ServiceListing listing) {
        Map<Long, com.unisphere.backend.commerce.services.dto.response.ServiceProviderResponse> actors =
                listingService.resolveProviders(Set.of(listing.getProviderId(), order.getClientId()));
        return new ServiceOrderResponse(
                order.getId(), order.getListingId(), listing.getTitle(),
                actors.get(listing.getProviderId()), actors.get(order.getClientId()),
                order.getRequirements(), order.getAgreedPrice(), order.getScheduledAt(),
                order.getStatus(), order.getDecisionReason(), order.getConversationId(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private ServiceOrderResponse toResponseWithoutListing(ServiceOrder order) {
        Map<Long, com.unisphere.backend.commerce.services.dto.response.ServiceProviderResponse> actors =
                listingService.resolveProviders(Set.of(order.getClientId()));
        return new ServiceOrderResponse(
                order.getId(), order.getListingId(), "Unknown",
                null, actors.get(order.getClientId()),
                order.getRequirements(), order.getAgreedPrice(), order.getScheduledAt(),
                order.getStatus(), order.getDecisionReason(), order.getConversationId(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
