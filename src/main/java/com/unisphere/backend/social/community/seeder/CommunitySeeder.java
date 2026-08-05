package com.unisphere.backend.social.community.seeder;

import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.community.entity.Community;
import com.unisphere.backend.social.community.entity.CommunityAnnouncement;
import com.unisphere.backend.social.community.entity.CommunityBan;
import com.unisphere.backend.social.community.entity.CommunityJoinRequest;
import com.unisphere.backend.social.community.entity.CommunityMember;
import com.unisphere.backend.social.community.entity.CommunityPost;
import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import com.unisphere.backend.social.community.enums.CommunityVisibility;
import com.unisphere.backend.social.community.repository.CommunityAnnouncementRepository;
import com.unisphere.backend.social.community.repository.CommunityBanRepository;
import com.unisphere.backend.social.community.repository.CommunityJoinRequestRepository;
import com.unisphere.backend.social.community.repository.CommunityMemberRepository;
import com.unisphere.backend.social.community.repository.CommunityPostRepository;
import com.unisphere.backend.social.community.repository.CommunityRepository;
import com.unisphere.backend.social.messaging.entity.Conversation;
import com.unisphere.backend.social.messaging.entity.ConversationMember;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import com.unisphere.backend.social.messaging.enums.MemberRole;
import com.unisphere.backend.social.messaging.repository.ConversationMemberRepository;
import com.unisphere.backend.social.messaging.repository.ConversationRepository;
import com.unisphere.backend.social.posting.entity.Post;
import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import com.unisphere.backend.social.posting.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Seeds sample communities, membership, bans, join requests, announcements, and community posts.
 * Runs after NewsSeeder (@Order(4)).
 * Guard: skips entirely if any community already exists.
 *
 * <p>Bypasses CommunityService/CommunityMembershipService on purpose, same as every other seeder
 * in this codebase bypasses its module's service layer — direct entity construction gives full
 * control over seed state without going through request/response DTOs. Because of that, the
 * community/conversation pairing and member_count bookkeeping those services normally handle
 * atomically is replicated here by hand via the helper methods below.
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class CommunitySeeder implements CommandLineRunner {

    private final UserRepository                    userRepository;
    private final CommunityRepository                communityRepository;
    private final CommunityMemberRepository          communityMemberRepository;
    private final CommunityBanRepository             communityBanRepository;
    private final CommunityJoinRequestRepository     communityJoinRequestRepository;
    private final CommunityAnnouncementRepository    communityAnnouncementRepository;
    private final CommunityPostRepository            communityPostRepository;
    private final ConversationRepository             conversationRepository;
    private final ConversationMemberRepository       conversationMemberRepository;
    private final PostRepository                     postRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (communityRepository.count() > 0) {
            log.info("Communities already seeded — skipping.");
            return;
        }

        Optional<User> studentOpt    = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt     = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> employerOpt   = userRepository.findByEmail("employer@unisphere.dev");
        Optional<User> clubOpt       = userRepository.findByEmail("club@unisphere.dev");
        Optional<User> universityOpt = userRepository.findByEmail("university@unisphere.dev");

        if (studentOpt.isEmpty() || alumniOpt.isEmpty() || employerOpt.isEmpty()
                || clubOpt.isEmpty() || universityOpt.isEmpty()) {
            log.warn("CommunitySeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User student    = studentOpt.get();
        User alumni     = alumniOpt.get();
        User employer   = employerOpt.get();
        User club       = clubOpt.get();
        User university = universityOpt.get();

        // ── Community 1: PUBLIC — anyone can join and post ──────────────────

        Community c1 = community(club.getId(), null, CommunityVisibility.PUBLIC,
                "Google Developer Student Club",
                "Open to anyone at UiTM interested in web, mobile, and cloud development. We share "
                + "resources, coordinate meetups, and help each other ship side projects.");
        addMember(c1, club.getId(), CommunityMemberRole.ADMIN);
        addMember(c1, student.getId(), CommunityMemberRole.MODERATOR);
        addMember(c1, alumni.getId(), CommunityMemberRole.MEMBER);
        syncMemberCount(c1);

        announcement(c1, club.getId(), "Welcome to GDSC!",
                "Glad to have you here. Introduce yourself in the chat and check the posts tab for "
                + "resources from past sessions.", true);
        announcement(c1, student.getId(), "This Saturday: Firebase workshop",
                "Same venue as usual, DKP2, 10am. Bring a laptop — we're building something end to end.", false);

        communityPost(c1, student.getId(), PostType.TEXT,
                "Anyone free to pair on the hackathon prep this week?",
                "Trying to get a team together before registration closes. Comment if you're in!");
        communityPost(c1, club.getId(), PostType.TEXT,
                "Recap: Firebase workshop slides are up",
                "Thanks to everyone who came out last Saturday. Slides and the sample repo are linked "
                + "in the chat pinned message.");

        // ── Community 2: UNIVERSITY_ONLY — scoped to UiTM ────────────────────
        // student and alumni are linked to `university` by UserSeeder.linkUniversityAffiliations(),
        // so this is real, exercisable UNIVERSITY_ONLY seed data rather than permanently empty.

        Community c2 = community(university.getId(), university.getId(), CommunityVisibility.UNIVERSITY_ONLY,
                "UiTM CS Majors",
                "For current UiTM Computer Science students and alumni only — course advice, FYP "
                + "war stories, and internship leads.");
        addMember(c2, university.getId(), CommunityMemberRole.ADMIN);
        addMember(c2, student.getId(), CommunityMemberRole.MEMBER);
        addMember(c2, alumni.getId(), CommunityMemberRole.MEMBER);
        syncMemberCount(c2);

        announcement(c2, university.getId(), "Revised exam timetable posted",
                "Check the pinned post in campus news for the updated Algorithm Analysis paper slot.", true);

        // ── Community 3: PRIVATE — join-request + approval, plus a ban ───────

        Community c3 = community(alumni.getId(), null, CommunityVisibility.PRIVATE,
                "Alumni Mentorship Circle",
                "Small, invite-reviewed group pairing recent grads with alumni mentors across the "
                + "industry. Request to join and tell us what you're looking for.");
        addMember(c3, alumni.getId(), CommunityMemberRole.ADMIN);
        addMember(c3, employer.getId(), CommunityMemberRole.MODERATOR);
        syncMemberCount(c3);

        announcement(c3, alumni.getId(), "Mentorship guidelines",
                "One 30-minute call a month, no obligation beyond that. Be respectful of everyone's "
                + "time and this stays valuable for the whole group.", true);

        CommunityJoinRequest pendingRequest = new CommunityJoinRequest();
        pendingRequest.setCommunityId(c3.getId());
        pendingRequest.setUserId(student.getId());
        pendingRequest.setMessage("Final-year CS student looking for guidance on breaking into backend roles.");
        communityJoinRequestRepository.save(pendingRequest);

        CommunityBan ban = new CommunityBan();
        ban.setCommunityId(c3.getId());
        ban.setUserId(club.getId());
        ban.setBannedBy(alumni.getId());
        ban.setReason("Off-topic recruiting posts unrelated to mentorship.");
        communityBanRepository.save(ban);

        log.info("Seeded 3 communities (public, university-only, private) with members, bans, "
                + "a pending join request, announcements, and {} community posts.", 2);
        log.info("Community seeding complete.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Creates the community and its single auto-provisioned chat conversation. */
    private Community community(Long createdBy, Long universityId, CommunityVisibility visibility,
                                String name, String description) {
        Community c = new Community();
        c.setCreatedBy(createdBy);
        c.setUniversityId(universityId);
        c.setVisibility(visibility);
        c.setName(name);
        c.setDescription(description);
        communityRepository.save(c);

        Conversation conv = new Conversation();
        conv.setConvType(ConversationType.COMMUNITY);
        conv.setName(name);
        conv.setCreatedBy(createdBy);
        conv.setCommunityId(c.getId());
        conversationRepository.save(conv);

        return c;
    }

    /** Mirrors CommunityMembershipService.addMembership: writes both the community and conversation side. */
    private void addMember(Community community, Long userId, CommunityMemberRole role) {
        CommunityMember member = new CommunityMember();
        member.setCommunityId(community.getId());
        member.setUserId(userId);
        member.setRole(role);
        communityMemberRepository.save(member);

        Conversation conversation = conversationRepository.findByCommunityId(community.getId())
                .orElseThrow(() -> new IllegalStateException("Seeder created no conversation for community " + community.getId()));
        ConversationMember conversationMember = new ConversationMember();
        conversationMember.setConversationId(conversation.getId());
        conversationMember.setUserId(userId);
        conversationMember.setRole(role == CommunityMemberRole.MEMBER ? MemberRole.MEMBER : MemberRole.ADMIN);
        conversationMemberRepository.save(conversationMember);
    }

    /** The Redis-buffered flush scheduler hasn't run yet, and member_count is direct-write anyway. */
    private void syncMemberCount(Community community) {
        long count = communityMemberRepository.findByCommunityId(community.getId()).size();
        community.setMemberCount((int) count);
        communityRepository.save(community);
    }

    private CommunityAnnouncement announcement(Community community, Long authorId, String title,
                                               String content, boolean pinned) {
        CommunityAnnouncement a = new CommunityAnnouncement();
        a.setCommunityId(community.getId());
        a.setAuthorId(authorId);
        a.setTitle(title);
        a.setContent(content);
        a.setPinned(pinned);
        return communityAnnouncementRepository.save(a);
    }

    private Post communityPost(Community community, Long userId, PostType type, String title, String content) {
        Post post = new Post();
        post.setUserId(userId);
        post.setPostType(type);
        post.setVisibility(PostVisibility.COMMUNITY);
        post.setTitle(title);
        post.setContent(content);
        postRepository.save(post);

        CommunityPost link = new CommunityPost();
        link.setCommunityId(community.getId());
        link.setPostId(post.getId());
        communityPostRepository.save(link);

        return post;
    }
}
