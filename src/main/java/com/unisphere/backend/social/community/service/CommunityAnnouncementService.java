package com.unisphere.backend.social.community.service;

import com.unisphere.backend.common.exception.CommunityAnnouncementNotFoundException;
import com.unisphere.backend.common.exception.CommunityNotFoundException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.community.dto.request.CreateAnnouncementRequest;
import com.unisphere.backend.social.community.dto.request.UpdateAnnouncementRequest;
import com.unisphere.backend.social.community.dto.response.CommunityAnnouncementResponse;
import com.unisphere.backend.social.community.entity.Community;
import com.unisphere.backend.social.community.entity.CommunityAnnouncement;
import com.unisphere.backend.social.community.repository.CommunityAnnouncementRepository;
import com.unisphere.backend.social.community.repository.CommunityMemberRepository;
import com.unisphere.backend.social.community.repository.CommunityRepository;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
public class CommunityAnnouncementService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityAnnouncementRepository communityAnnouncementRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final CommunityAccessService communityAccessService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public CommunityAnnouncementResponse create(Long communityId, CreateAnnouncementRequest req, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());

        CommunityAnnouncement announcement = new CommunityAnnouncement();
        announcement.setCommunityId(communityId);
        announcement.setAuthorId(currentUser.getId());
        announcement.setTitle(req.title());
        announcement.setContent(req.content());
        communityAnnouncementRepository.save(announcement);

        CommunityAnnouncementResponse response = toResponse(announcement, currentUser);

        // Persisted notification per member (createAndPush no-ops for the author, since
        // userId.equals(actorId)) plus a live broadcast for anyone with the community open.
        communityMemberRepository.findByCommunityId(communityId).forEach(m ->
                notificationService.createAndPush(m.getUserId(), currentUser.getId(),
                        NotificationType.COMMUNITY_ANNOUNCEMENT, announcement.getId(), "COMMUNITY_ANNOUNCEMENT"));
        messagingTemplate.convertAndSend("/topic/community/" + communityId + "/announcements", response);

        return response;
    }

    @Transactional(readOnly = true)
    public Page<CommunityAnnouncementResponse> list(Long communityId, Pageable pageable, User currentUser) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        if (!communityAccessService.canViewContent(community, currentUser)) {
            throw new CommunityNotFoundException(communityId);
        }
        Page<CommunityAnnouncement> page =
                communityAnnouncementRepository.findByCommunityIdOrderByPinnedDescCreatedAtDesc(communityId, pageable);
        Map<Long, User> authors = loadAuthors(page.getContent());
        return page.map(a -> buildResponse(a, authors.get(a.getAuthorId())));
    }

    /** Any moderator/admin may edit any announcement — a deliberate "team-authored" model. */
    public CommunityAnnouncementResponse update(Long communityId, Long announcementId,
                                                UpdateAnnouncementRequest req, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        CommunityAnnouncement announcement = findInCommunity(communityId, announcementId);

        if (req.title() != null) announcement.setTitle(req.title());
        if (req.content() != null) announcement.setContent(req.content());

        return toResponse(communityAnnouncementRepository.save(announcement), currentUser);
    }

    public void delete(Long communityId, Long announcementId, User currentUser) {
        communityAccessService.assertModerator(communityId, currentUser.getId());
        CommunityAnnouncement announcement = findInCommunity(communityId, announcementId);
        announcement.setDeletedAt(LocalDateTime.now());
        communityAnnouncementRepository.save(announcement);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CommunityAnnouncement findInCommunity(Long communityId, Long announcementId) {
        CommunityAnnouncement announcement = communityAnnouncementRepository.findActiveById(announcementId)
                .orElseThrow(() -> new CommunityAnnouncementNotFoundException(announcementId));
        if (!announcement.getCommunityId().equals(communityId)) {
            throw new CommunityAnnouncementNotFoundException(announcementId);
        }
        return announcement;
    }

    private Map<Long, User> loadAuthors(List<CommunityAnnouncement> announcements) {
        Set<Long> authorIds = announcements.stream()
                .map(CommunityAnnouncement::getAuthorId).collect(Collectors.toSet());
        if (authorIds.isEmpty()) return Map.of();
        return userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private CommunityAnnouncementResponse toResponse(CommunityAnnouncement a, User currentUser) {
        User author = a.getAuthorId().equals(currentUser.getId())
                ? currentUser
                : userRepository.findById(a.getAuthorId()).orElse(null);
        return buildResponse(a, author);
    }

    private CommunityAnnouncementResponse buildResponse(CommunityAnnouncement a, User author) {
        return new CommunityAnnouncementResponse(
                a.getId(), a.getCommunityId(), a.getAuthorId(),
                author == null ? "Unknown" : UserService.resolveDisplayName(author),
                author == null ? null : mediaUrlResolver.toViewableUrl(author.getAvatarUrl()),
                a.getTitle(), a.getContent(), a.isPinned(), a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
