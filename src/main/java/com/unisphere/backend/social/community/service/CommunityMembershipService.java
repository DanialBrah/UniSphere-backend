package com.unisphere.backend.social.community.service;

import com.unisphere.backend.common.exception.CommunityJoinRequestNotFoundException;
import com.unisphere.backend.common.exception.CommunityNotFoundException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.community.dto.request.BanRequest;
import com.unisphere.backend.social.community.dto.request.CreateJoinRequestRequest;
import com.unisphere.backend.social.community.dto.response.CommunityBanResponse;
import com.unisphere.backend.social.community.dto.response.CommunityJoinRequestResponse;
import com.unisphere.backend.social.community.dto.response.CommunityMemberResponse;
import com.unisphere.backend.social.community.entity.Community;
import com.unisphere.backend.social.community.entity.CommunityBan;
import com.unisphere.backend.social.community.entity.CommunityJoinRequest;
import com.unisphere.backend.social.community.entity.CommunityMember;
import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import com.unisphere.backend.social.community.enums.JoinRequestStatus;
import com.unisphere.backend.social.community.repository.CommunityBanRepository;
import com.unisphere.backend.social.community.repository.CommunityJoinRequestRepository;
import com.unisphere.backend.social.community.repository.CommunityMemberRepository;
import com.unisphere.backend.social.community.repository.CommunityRepository;
import com.unisphere.backend.social.messaging.entity.ConversationMember;
import com.unisphere.backend.social.messaging.enums.MemberRole;
import com.unisphere.backend.social.messaging.repository.ConversationMemberRepository;
import com.unisphere.backend.social.messaging.repository.ConversationRepository;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The single chokepoint for every membership write. Keeps {@code community_members} and a
 * mirrored {@code conversation_members} row in sync in one transaction, so the existing
 * ConversationService/MessageService/WebSocket stack can be reused for community chat completely
 * unmodified — see ConversationService's COMMUNITY guards, which stop that mirror from being
 * bypassed through the generic conversation endpoints.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CommunityMembershipService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityBanRepository communityBanRepository;
    private final CommunityJoinRequestRepository communityJoinRequestRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final CommunityAccessService communityAccessService;
    private final NotificationService notificationService;

    /** Package-private — called only from CommunityService.createCommunity. */
    void addFounderMembership(Long communityId, Long conversationId, Long userId) {
        addMembership(communityId, conversationId, userId, CommunityMemberRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public Page<CommunityMemberResponse> listMembers(Long communityId, Pageable pageable, User currentUser) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        if (!communityAccessService.canViewContent(community, currentUser)) {
            throw new CommunityNotFoundException(communityId);
        }
        Page<CommunityMember> page = communityMemberRepository.findByCommunityId(communityId, pageable);
        Map<Long, User> users = loadUsers(page.getContent().stream()
                .map(CommunityMember::getUserId).collect(Collectors.toSet()));
        return page.map(m -> toMemberResponse(m, users.get(m.getUserId())));
    }

    public CommunityMemberResponse joinSelf(Long communityId, User currentUser) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        if (community.getVisibility() == com.unisphere.backend.social.community.enums.CommunityVisibility.PRIVATE) {
            throw new IllegalArgumentException("This community is private — request to join instead");
        }
        Long userId = currentUser.getId();
        if (communityBanRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new IllegalArgumentException("You have been banned from this community");
        }
        if (communityMemberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new IllegalArgumentException("You are already a member of this community");
        }

        Long conversationId = conversationIdFor(communityId);
        CommunityMember member = addMembership(communityId, conversationId, userId, CommunityMemberRole.MEMBER);
        return toMemberResponse(member, currentUser);
    }

    public void leave(Long communityId, User currentUser) {
        Long userId = currentUser.getId();
        CommunityMember member = communityMemberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(com.unisphere.backend.common.exception.NotCommunityMemberException::new);

        if (member.getRole() == CommunityMemberRole.ADMIN
                && communityMemberRepository.countByCommunityIdAndRole(communityId, CommunityMemberRole.ADMIN) <= 1) {
            throw new IllegalArgumentException(
                    "You are the only admin — promote another admin or delete the community first");
        }

        removeMembership(communityId, conversationIdFor(communityId), userId);
    }

    public CommunityMemberResponse changeRole(Long communityId, Long targetUserId, CommunityMemberRole newRole,
                                              User currentUser) {
        communityAccessService.assertAdmin(communityId, currentUser.getId());
        CommunityMember member = communityMemberRepository.findByCommunityIdAndUserId(communityId, targetUserId)
                .orElseThrow(com.unisphere.backend.common.exception.NotCommunityMemberException::new);

        if (member.getRole() == CommunityMemberRole.ADMIN && newRole != CommunityMemberRole.ADMIN
                && communityMemberRepository.countByCommunityIdAndRole(communityId, CommunityMemberRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("Cannot demote the only remaining admin");
        }

        member.setRole(newRole);
        communityMemberRepository.save(member);

        conversationMemberRepository
                .findByConversationIdAndUserId(conversationIdFor(communityId), targetUserId)
                .ifPresent(cm -> {
                    cm.setRole(newRole == CommunityMemberRole.MEMBER ? MemberRole.MEMBER : MemberRole.ADMIN);
                    conversationMemberRepository.save(cm);
                });

        notificationService.createAndPush(targetUserId, currentUser.getId(),
                NotificationType.COMMUNITY_ROLE_CHANGED, communityId, "COMMUNITY");

        User user = userRepository.findById(targetUserId).orElse(null);
        return toMemberResponse(member, user);
    }

    public void kick(Long communityId, Long targetUserId, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        assertCanActOnRole(communityId, currentUser.getId(), targetUserId);
        removeMembership(communityId, conversationIdFor(communityId), targetUserId);
    }

    // ── Join requests ────────────────────────────────────────────────────────

    public CommunityJoinRequestResponse requestToJoin(Long communityId, CreateJoinRequestRequest req, User currentUser) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        if (community.getVisibility() != com.unisphere.backend.social.community.enums.CommunityVisibility.PRIVATE) {
            throw new IllegalArgumentException("Only private communities require a join request — join directly instead");
        }
        Long userId = currentUser.getId();
        if (communityBanRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new IllegalArgumentException("You have been banned from this community");
        }
        if (communityMemberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new IllegalArgumentException("You are already a member of this community");
        }
        if (communityJoinRequestRepository.existsByCommunityIdAndUserIdAndStatus(communityId, userId, JoinRequestStatus.PENDING)) {
            throw new IllegalArgumentException("You already have a pending join request for this community");
        }

        CommunityJoinRequest request = new CommunityJoinRequest();
        request.setCommunityId(communityId);
        request.setUserId(userId);
        request.setMessage(req.message());
        communityJoinRequestRepository.save(request);

        communityMemberRepository.findByCommunityId(communityId).stream()
                .filter(m -> m.getRole() == CommunityMemberRole.ADMIN || m.getRole() == CommunityMemberRole.MODERATOR)
                .forEach(m -> notificationService.createAndPush(m.getUserId(), userId,
                        NotificationType.COMMUNITY_JOIN_REQUEST, request.getId(), "COMMUNITY_JOIN_REQUEST"));

        return toJoinRequestResponse(request, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<CommunityJoinRequestResponse> listJoinRequests(Long communityId, JoinRequestStatus status,
                                                                Pageable pageable, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        Page<CommunityJoinRequest> page = communityJoinRequestRepository.findByCommunityIdAndStatus(
                communityId, status != null ? status : JoinRequestStatus.PENDING, pageable);
        Map<Long, User> users = loadUsers(page.getContent().stream()
                .map(CommunityJoinRequest::getUserId).collect(Collectors.toSet()));
        return page.map(r -> toJoinRequestResponse(r, users.get(r.getUserId())));
    }

    public CommunityJoinRequestResponse approveJoinRequest(Long communityId, Long requestId, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        CommunityJoinRequest request = communityJoinRequestRepository.findById(requestId)
                .filter(r -> r.getCommunityId().equals(communityId))
                .orElseThrow(() -> new CommunityJoinRequestNotFoundException(requestId));
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new IllegalArgumentException("This join request has already been reviewed");
        }

        request.setStatus(JoinRequestStatus.APPROVED);
        request.setReviewedBy(currentUser.getId());
        request.setReviewedAt(LocalDateTime.now());
        communityJoinRequestRepository.save(request);

        addMembership(communityId, conversationIdFor(communityId), request.getUserId(), CommunityMemberRole.MEMBER);

        notificationService.createAndPush(request.getUserId(), currentUser.getId(),
                NotificationType.COMMUNITY_JOIN_APPROVED, communityId, "COMMUNITY");

        User user = userRepository.findById(request.getUserId()).orElse(null);
        return toJoinRequestResponse(request, user);
    }

    public CommunityJoinRequestResponse rejectJoinRequest(Long communityId, Long requestId, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        CommunityJoinRequest request = communityJoinRequestRepository.findById(requestId)
                .filter(r -> r.getCommunityId().equals(communityId))
                .orElseThrow(() -> new CommunityJoinRequestNotFoundException(requestId));
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new IllegalArgumentException("This join request has already been reviewed");
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setReviewedBy(currentUser.getId());
        request.setReviewedAt(LocalDateTime.now());
        communityJoinRequestRepository.save(request);

        notificationService.createAndPush(request.getUserId(), currentUser.getId(),
                NotificationType.COMMUNITY_JOIN_REJECTED, communityId, "COMMUNITY");

        User user = userRepository.findById(request.getUserId()).orElse(null);
        return toJoinRequestResponse(request, user);
    }

    // ── Bans ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CommunityBanResponse> listBans(Long communityId, Pageable pageable, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        Page<CommunityBan> page = communityBanRepository.findByCommunityId(communityId, pageable);
        Map<Long, User> users = loadUsers(page.getContent().stream()
                .map(CommunityBan::getUserId).collect(Collectors.toSet()));
        return page.map(b -> toBanResponse(b, users.get(b.getUserId())));
    }

    public CommunityBanResponse ban(Long communityId, Long targetUserId, BanRequest req, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        assertCanActOnRole(communityId, currentUser.getId(), targetUserId);

        if (communityBanRepository.existsByCommunityIdAndUserId(communityId, targetUserId)) {
            throw new IllegalArgumentException("This user is already banned");
        }

        if (communityMemberRepository.existsByCommunityIdAndUserId(communityId, targetUserId)) {
            removeMembership(communityId, conversationIdFor(communityId), targetUserId);
        }

        CommunityBan ban = new CommunityBan();
        ban.setCommunityId(communityId);
        ban.setUserId(targetUserId);
        ban.setBannedBy(currentUser.getId());
        ban.setReason(req.reason());
        communityBanRepository.save(ban);

        User user = userRepository.findById(targetUserId).orElse(null);
        return toBanResponse(ban, user);
    }

    public void unban(Long communityId, Long targetUserId, User currentUser) {
        communityAccessService.assertAdmin(communityId, currentUser.getId());
        communityBanRepository.deleteByCommunityIdAndUserId(communityId, targetUserId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Long conversationIdFor(Long communityId) {
        return conversationRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId))
                .getId();
    }

    /**
     * A moderator may only act on a member; only an admin may act on a moderator or another
     * admin, and never on the last remaining admin. A target who isn't currently a member (a
     * preemptive ban) is treated as the lowest privilege level — there's nothing to escalate
     * past, and {@code ban} must work on non-members for that case to be reachable at all.
     */
    private void assertCanActOnRole(Long communityId, Long actingUserId, Long targetUserId) {
        CommunityMemberRole actingRole = communityAccessService.roleOf(communityId, actingUserId)
                .orElseThrow(com.unisphere.backend.common.exception.NotCommunityMemberException::new);
        CommunityMemberRole targetRole = communityAccessService.roleOf(communityId, targetUserId)
                .orElse(CommunityMemberRole.MEMBER);

        if (targetRole != CommunityMemberRole.MEMBER && actingRole != CommunityMemberRole.ADMIN) {
            throw new com.unisphere.backend.common.exception.UnauthorizedActionException(
                    "Only an admin can act on a moderator or admin");
        }
        if (targetRole == CommunityMemberRole.ADMIN
                && communityMemberRepository.countByCommunityIdAndRole(communityId, CommunityMemberRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("Cannot remove the only remaining admin");
        }
    }

    private CommunityMember addMembership(Long communityId, Long conversationId, Long userId, CommunityMemberRole role) {
        CommunityMember member = new CommunityMember();
        member.setCommunityId(communityId);
        member.setUserId(userId);
        member.setRole(role);
        communityMemberRepository.save(member);

        ConversationMember conversationMember = new ConversationMember();
        conversationMember.setConversationId(conversationId);
        conversationMember.setUserId(userId);
        conversationMember.setRole(role == CommunityMemberRole.MEMBER ? MemberRole.MEMBER : MemberRole.ADMIN);
        conversationMemberRepository.save(conversationMember);

        communityRepository.incrementMemberCount(communityId, 1);
        return member;
    }

    private void removeMembership(Long communityId, Long conversationId, Long userId) {
        communityMemberRepository.deleteByCommunityIdAndUserId(communityId, userId);
        conversationMemberRepository.deleteByConversationIdAndUserId(conversationId, userId);
        communityRepository.incrementMemberCount(communityId, -1);
    }

    private Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private CommunityMemberResponse toMemberResponse(CommunityMember member, User user) {
        if (user == null) {
            return new CommunityMemberResponse(member.getUserId(), "Unknown", null, member.getRole(), member.getJoinedAt());
        }
        return new CommunityMemberResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), member.getRole(), member.getJoinedAt());
    }

    private CommunityBanResponse toBanResponse(CommunityBan ban, User user) {
        if (user == null) {
            return new CommunityBanResponse(ban.getUserId(), "Unknown", null, ban.getBannedBy(), ban.getReason(), ban.getBannedAt());
        }
        return new CommunityBanResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), ban.getBannedBy(), ban.getReason(), ban.getBannedAt());
    }

    private CommunityJoinRequestResponse toJoinRequestResponse(CommunityJoinRequest request, User user) {
        String name = user == null ? "Unknown" : UserService.resolveDisplayName(user);
        String avatar = user == null ? null : mediaUrlResolver.toViewableUrl(user.getAvatarUrl());
        return new CommunityJoinRequestResponse(request.getId(), request.getUserId(), name, avatar,
                request.getStatus(), request.getMessage(), request.getReviewedBy(), request.getReviewedAt(),
                request.getCreatedAt());
    }
}
