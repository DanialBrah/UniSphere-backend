package com.unisphere.backend.social.community.service;

import com.unisphere.backend.common.exception.CommunityNotFoundException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.community.dto.request.CreateCommunityRequest;
import com.unisphere.backend.social.community.dto.request.UpdateCommunityRequest;
import com.unisphere.backend.social.community.dto.response.CommunityResponse;
import com.unisphere.backend.social.community.entity.Community;
import com.unisphere.backend.social.community.entity.CommunityJoinRequest;
import com.unisphere.backend.social.community.entity.CommunityMember;
import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import com.unisphere.backend.social.community.enums.CommunityVisibility;
import com.unisphere.backend.social.community.enums.JoinRequestStatus;
import com.unisphere.backend.social.community.repository.CommunityAnnouncementRepository;
import com.unisphere.backend.social.community.repository.CommunityJoinRequestRepository;
import com.unisphere.backend.social.community.repository.CommunityMemberRepository;
import com.unisphere.backend.social.community.repository.CommunityRepository;
import com.unisphere.backend.social.messaging.entity.Conversation;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import com.unisphere.backend.social.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityJoinRequestRepository communityJoinRequestRepository;
    private final CommunityAnnouncementRepository communityAnnouncementRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final CommunityAccessService communityAccessService;
    private final CommunityMembershipService communityMembershipService;

    public CommunityResponse createCommunity(CreateCommunityRequest req, User currentUser) {
        CommunityVisibility visibility = req.visibility() != null ? req.visibility() : CommunityVisibility.PUBLIC;

        Community community = new Community();
        community.setCreatedBy(currentUser.getId());
        community.setName(req.name());
        community.setDescription(req.description());
        community.setBannerKey(req.bannerKey());
        community.setVisibility(visibility);
        // Never trust a client-supplied universityId for UNIVERSITY_ONLY — its guarantee depends
        // on this actually matching the creator's own affiliation, not an arbitrary client value.
        community.setUniversityId(visibility == CommunityVisibility.UNIVERSITY_ONLY
                ? communityAccessService.viewerUniversityId(currentUser)
                : req.universityId());
        communityRepository.save(community);

        Conversation conversation = new Conversation();
        conversation.setConvType(ConversationType.COMMUNITY);
        conversation.setName(community.getName());
        conversation.setCreatedBy(currentUser.getId());
        conversation.setCommunityId(community.getId());
        conversationRepository.save(conversation);

        communityMembershipService.addFounderMembership(community.getId(), conversation.getId(), currentUser.getId());
        // incrementMemberCount runs as a direct SQL UPDATE bypassing the persistence context, so
        // the in-memory entity above is now stale relative to the DB row — reflect it locally
        // rather than re-fetching just to render the response.
        community.setMemberCount(1);

        return toResponse(community, currentUser);
    }

    @Transactional(readOnly = true)
    public CommunityResponse getCommunity(Long id, User currentUser) {
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        return toResponse(community, currentUser);
    }

    /**
     * The discovery feed. Deliberately not visibility-filtered — see
     * {@link CommunityRepository#search} for why a community's existence is always discoverable.
     */
    @Transactional(readOnly = true)
    public Page<CommunityResponse> listDiscoverable(Pageable pageable, User currentUser) {
        return toResponses(communityRepository.findAll(pageable), currentUser);
    }

    @Transactional(readOnly = true)
    public Page<CommunityResponse> search(String query, Pageable pageable, User currentUser) {
        return toResponses(communityRepository.search(query, pageable), currentUser);
    }

    @Transactional(readOnly = true)
    public Page<CommunityResponse> listMine(Pageable pageable, User currentUser) {
        return toResponses(communityRepository.findAllByMemberId(currentUser.getId(), pageable), currentUser);
    }

    public CommunityResponse updateCommunity(Long id, UpdateCommunityRequest req, User currentUser) {
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        communityAccessService.assertAdmin(id, currentUser.getId());

        if (req.name() != null) community.setName(req.name());
        if (req.description() != null) community.setDescription(req.description());
        if (req.bannerKey() != null) {
            community.setBannerKey(req.bannerKey().isBlank() ? null : req.bannerKey());
        }
        if (req.visibility() != null) {
            community.setVisibility(req.visibility());
            if (req.visibility() == CommunityVisibility.UNIVERSITY_ONLY) {
                // Derived from the community's own creator, not the caller issuing the update —
                // an admin acting on someone else's community shouldn't stamp their own
                // affiliation onto it. Re-derived rather than left as-is: switching into
                // UNIVERSITY_ONLY on a community whose universityId is still null would make it
                // invisible to everyone.
                User creator = userRepository.findById(community.getCreatedBy())
                        .orElseThrow(() -> new CommunityNotFoundException(id));
                community.setUniversityId(communityAccessService.viewerUniversityId(creator));
            }
        }

        return toResponse(communityRepository.save(community), currentUser);
    }

    public void deleteCommunity(Long id, User currentUser) {
        Community community = communityRepository.findActiveById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        communityAccessService.assertAdmin(id, currentUser.getId());

        LocalDateTime now = LocalDateTime.now();
        community.setDeletedAt(now);
        communityRepository.save(community);

        // A live conversation would otherwise keep appearing in members' DM inbox after the
        // community is "gone". Membership/ban/join-request/post-link rows are deliberately left
        // in place, matching the existing codebase-wide pattern where deleting a Post doesn't
        // clean up post_likes/comments/post_media.
        conversationRepository.findByCommunityId(id).ifPresent(conv -> {
            conv.setDeletedAt(now);
            conversationRepository.save(conv);
        });

        communityAnnouncementRepository.softDeleteByCommunityId(id, now);
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    /**
     * Page-level mapping: creators, the viewer's role, and pending-join-request flags are
     * batch-loaded once for the whole page. Mapping community-by-community would issue those
     * lookups per row, so a 20-community page would cost 60 extra round-trips.
     */
    private Page<CommunityResponse> toResponses(Page<Community> communities, User currentUser) {
        PageContext ctx = loadPageContext(communities.getContent(), currentUser);
        return communities.map(c -> toResponse(c, ctx));
    }

    private record PageContext(Map<Long, User> creators, Map<Long, CommunityMemberRole> viewerRoles,
                               Set<Long> pendingRequestCommunityIds) {}

    private PageContext loadPageContext(List<Community> communities, User currentUser) {
        if (communities.isEmpty()) return new PageContext(Map.of(), Map.of(), Set.of());

        Set<Long> creatorIds = communities.stream().map(Community::getCreatedBy).collect(Collectors.toSet());
        Set<Long> communityIds = communities.stream().map(Community::getId).collect(Collectors.toSet());

        Map<Long, User> creators = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, CommunityMemberRole> viewerRoles =
                communityMemberRepository.findByCommunityIdInAndUserId(communityIds, currentUser.getId()).stream()
                        .collect(Collectors.toMap(CommunityMember::getCommunityId, CommunityMember::getRole));

        Set<Long> pending = communityJoinRequestRepository
                .findByCommunityIdInAndUserIdAndStatus(communityIds, currentUser.getId(), JoinRequestStatus.PENDING)
                .stream()
                .map(CommunityJoinRequest::getCommunityId)
                .collect(Collectors.toSet());

        return new PageContext(creators, viewerRoles, pending);
    }

    CommunityResponse toResponse(Community community, User currentUser) {
        return toResponse(community, loadPageContext(List.of(community), currentUser));
    }

    private CommunityResponse toResponse(Community c, PageContext ctx) {
        User creator = ctx.creators().get(c.getCreatedBy());
        return new CommunityResponse(
                c.getId(), c.getCreatedBy(),
                creator == null ? "Unknown" : UserService.resolveDisplayName(creator),
                creator == null ? null : mediaUrlResolver.toViewableUrl(creator.getAvatarUrl()),
                c.getName(), c.getDescription(), mediaUrlResolver.toViewableUrl(c.getBannerKey()),
                c.getVisibility(), c.getUniversityId(), c.getMemberCount(),
                ctx.viewerRoles().get(c.getId()),
                ctx.pendingRequestCommunityIds().contains(c.getId()),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
