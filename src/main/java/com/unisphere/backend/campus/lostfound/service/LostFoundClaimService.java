package com.unisphere.backend.campus.lostfound.service;

import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundClaimRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundClaimDecisionRequest;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundClaimResponse;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundUserResponse;
import com.unisphere.backend.campus.lostfound.entity.LostFoundClaim;
import com.unisphere.backend.campus.lostfound.entity.LostFoundItem;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.repository.LostFoundClaimRepository;
import com.unisphere.backend.campus.lostfound.repository.LostFoundItemRepository;
import com.unisphere.backend.common.exception.InvalidLostFoundClaimTransitionException;
import com.unisphere.backend.common.exception.LostFoundClaimNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The claim lifecycle: submit, list, decide.
 *
 * <p>Modelled on {@code social.community.service.CommunityMembershipService}'s join-request flow,
 * with one addition — approving a claim auto-rejects every sibling, because leaving three PENDING
 * claims on an already-CLAIMED item is a state nobody can act on.
 *
 * <p>Notifications use {@code targetType = "LOST_FOUND_ITEM"} with the <em>claim</em> id as the
 * target, so a client can deep-link straight to the claim being decided.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LostFoundClaimService {

    private static final String NOTIFICATION_TARGET_TYPE = "LOST_FOUND_ITEM";

    private final LostFoundClaimRepository claimRepository;
    private final LostFoundItemRepository itemRepository;
    private final UserRepository userRepository;
    private final LostFoundAccessService accessService;
    private final LostFoundMediaService mediaService;
    private final LostFoundService lostFoundService;
    private final NotificationService notificationService;
    private final MediaUrlResolver mediaUrlResolver;

    public LostFoundClaimResponse submitClaim(Long itemId, CreateLostFoundClaimRequest req, User currentUser) {
        LostFoundItem item = lostFoundService.findActiveItem(itemId);

        if (item.getReportedBy().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You cannot claim an item you reported yourself");
        }
        if (item.getStatus() != LostFoundItemStatus.OPEN) {
            throw new IllegalArgumentException("This item is no longer open for claims");
        }
        // The table deliberately has no unique constraint (a rejected claimant may re-apply), so
        // the "one live claim at a time" rule is enforced here.
        if (claimRepository.existsByItemIdAndClaimantIdAndStatus(
                itemId, currentUser.getId(), LostFoundClaimStatus.PENDING)) {
            throw new IllegalArgumentException("You already have a pending claim on this item");
        }
        if (req.proofImageKey() != null && !req.proofImageKey().isBlank()) {
            mediaService.assertOwnedKey(req.proofImageKey(), currentUser);
        }

        LostFoundClaim claim = new LostFoundClaim();
        claim.setItemId(itemId);
        claim.setClaimantId(currentUser.getId());
        claim.setStatus(LostFoundClaimStatus.PENDING);
        claim.setProofText(req.proofText());
        claim.setProofImageKey(blankToNull(req.proofImageKey()));
        claim = claimRepository.save(claim);

        notificationService.createAndPush(item.getReportedBy(), currentUser.getId(),
                NotificationType.LOST_FOUND_CLAIM, claim.getId(), NOTIFICATION_TARGET_TYPE);

        return toResponse(claim, item.getTitle(), currentUser);
    }

    /**
     * Claims on an item. The reporter and admins see every claim; a claimant sees only their own;
     * anyone else is refused — a public list would expose every claimant's proof text.
     */
    @Transactional(readOnly = true)
    public Page<LostFoundClaimResponse> listClaimsForItem(Long itemId, LostFoundClaimStatus status,
                                                          Pageable pageable, User currentUser) {
        LostFoundItem item = lostFoundService.findActiveItem(itemId);

        Page<LostFoundClaim> claims;
        if (accessService.isOwnerOrAdmin(item, currentUser)) {
            claims = claimRepository.findByItem(itemId, status, pageable);
        } else if (claimRepository.existsByItemIdAndClaimantId(itemId, currentUser.getId())) {
            claims = claimRepository.findByItemAndClaimant(itemId, currentUser.getId(), status, pageable);
        } else {
            throw new UnauthorizedActionException("You cannot view claims on this item");
        }
        return toResponses(claims, item.getTitle());
    }

    @Transactional(readOnly = true)
    public Page<LostFoundClaimResponse> listMyClaims(LostFoundClaimStatus status, Pageable pageable,
                                                     User currentUser) {
        Page<LostFoundClaim> claims = claimRepository.findByClaimant(currentUser.getId(), status, pageable);
        Map<Long, String> titles = itemTitles(claims.getContent());
        Map<Long, User> claimants = Map.of(currentUser.getId(), currentUser);
        return claims.map(c -> toResponse(c, titles.get(c.getItemId()), claimants));
    }

    @Transactional(readOnly = true)
    public LostFoundClaimResponse getClaim(Long claimId, User currentUser) {
        LostFoundClaim claim = findClaim(claimId);
        LostFoundItem item = lostFoundService.findActiveItem(claim.getItemId());
        if (!claim.getClaimantId().equals(currentUser.getId())
                && !accessService.isOwnerOrAdmin(item, currentUser)) {
            throw new UnauthorizedActionException("You cannot view this claim");
        }
        return toResponse(claim, item.getTitle(), loadUser(claim.getClaimantId()));
    }

    /**
     * Approve, reject or cancel. The permitted target depends on who is asking, so the branch is on
     * the requested status rather than on the caller's role.
     */
    public LostFoundClaimResponse decideClaim(Long claimId, LostFoundClaimDecisionRequest req, User currentUser) {
        LostFoundClaim claim = findClaim(claimId);
        LostFoundItem item = lostFoundService.findActiveItem(claim.getItemId());

        if (claim.getStatus() != LostFoundClaimStatus.PENDING) {
            throw new InvalidLostFoundClaimTransitionException(claim.getStatus(), req.status());
        }

        switch (req.status()) {
            case CANCELLED -> {
                if (!claim.getClaimantId().equals(currentUser.getId())) {
                    throw new UnauthorizedActionException("Only the claimant can cancel this claim");
                }
                claim.setStatus(LostFoundClaimStatus.CANCELLED);
                claim.setDecisionNote(req.decisionNote());
                // reviewedBy stays null — withdrawing is not a review.
            }
            case APPROVED -> {
                accessService.assertCanDecideClaim(item, currentUser);
                approve(claim, item, req, currentUser);
            }
            case REJECTED -> {
                accessService.assertCanDecideClaim(item, currentUser);
                claim.setStatus(LostFoundClaimStatus.REJECTED);
                claim.setDecisionNote(req.decisionNote());
                claim.setReviewedBy(currentUser.getId());
                claim.setReviewedAt(LocalDateTime.now());
                notifyClaimant(claim, currentUser);
            }
            default -> throw new InvalidLostFoundClaimTransitionException(claim.getStatus(), req.status());
        }

        return toResponse(claimRepository.save(claim), item.getTitle(), loadUser(claim.getClaimantId()));
    }

    /**
     * Approving does three things in one transaction: settles this claim, moves the item to
     * CLAIMED, and closes out every sibling claim so the item does not sit there with pending
     * claims nobody will ever act on.
     */
    private void approve(LostFoundClaim claim, LostFoundItem item,
                         LostFoundClaimDecisionRequest req, User currentUser) {
        LocalDateTime now = LocalDateTime.now();

        claim.setStatus(LostFoundClaimStatus.APPROVED);
        claim.setDecisionNote(req.decisionNote());
        claim.setReviewedBy(currentUser.getId());
        claim.setReviewedAt(now);

        item.setStatus(LostFoundItemStatus.CLAIMED);
        itemRepository.save(item);

        List<LostFoundClaim> siblings = claimRepository
                .findByItemIdAndStatus(item.getId(), LostFoundClaimStatus.PENDING).stream()
                .filter(c -> !c.getId().equals(claim.getId()))
                .toList();

        for (LostFoundClaim sibling : siblings) {
            sibling.setStatus(LostFoundClaimStatus.REJECTED);
            sibling.setDecisionNote("Another claim was approved");
            sibling.setReviewedBy(currentUser.getId());
            sibling.setReviewedAt(now);
            notifyClaimant(sibling, currentUser);
        }
        claimRepository.saveAll(siblings);

        notifyClaimant(claim, currentUser);
    }

    private void notifyClaimant(LostFoundClaim claim, User actor) {
        notificationService.createAndPush(claim.getClaimantId(), actor.getId(),
                NotificationType.LOST_FOUND_CLAIM, claim.getId(), NOTIFICATION_TARGET_TYPE);
    }

    private LostFoundClaim findClaim(Long claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new LostFoundClaimNotFoundException(claimId));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    /** Batch-loads claimants once for the page rather than per row. */
    private Page<LostFoundClaimResponse> toResponses(Page<LostFoundClaim> claims, String itemTitle) {
        Map<Long, User> claimants = loadUsers(claims.getContent());
        return claims.map(c -> toResponse(c, itemTitle, claimants));
    }

    private Map<Long, User> loadUsers(List<LostFoundClaim> claims) {
        if (claims.isEmpty()) return Map.of();
        Set<Long> ids = claims.stream().map(LostFoundClaim::getClaimantId).collect(Collectors.toSet());
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    /** Titles for a page of claims spanning many items — one query, not one per claim. */
    private Map<Long, String> itemTitles(List<LostFoundClaim> claims) {
        if (claims.isEmpty()) return Map.of();
        Set<Long> itemIds = claims.stream().map(LostFoundClaim::getItemId).collect(Collectors.toSet());
        return itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(LostFoundItem::getId, LostFoundItem::getTitle));
    }

    private Map<Long, User> loadUser(Long userId) {
        return userRepository.findById(userId)
                .map(u -> Map.of(u.getId(), u))
                .orElseGet(Map::of);
    }

    private LostFoundClaimResponse toResponse(LostFoundClaim claim, String itemTitle, User claimant) {
        return toResponse(claim, itemTitle, Map.of(claimant.getId(), claimant));
    }

    private LostFoundClaimResponse toResponse(LostFoundClaim claim, String itemTitle, Map<Long, User> claimants) {
        return new LostFoundClaimResponse(
                claim.getId(), claim.getItemId(), itemTitle,
                resolveUser(claim.getClaimantId(), claimants),
                claim.getStatus(), claim.getProofText(),
                mediaUrlResolver.toViewableUrl(claim.getProofImageKey()),
                claim.getDecisionNote(), claim.getReviewedBy(), claim.getReviewedAt(),
                claim.getCreatedAt(), claim.getUpdatedAt());
    }

    private LostFoundUserResponse resolveUser(Long userId, Map<Long, User> users) {
        User user = users.get(userId);
        if (user == null) return new LostFoundUserResponse(userId, "Unknown", null, "UNKNOWN");
        // avatar_url holds a bare key since changeset 012 — it has to be signed to be fetchable.
        return new LostFoundUserResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), user.getRole().name());
    }
}
