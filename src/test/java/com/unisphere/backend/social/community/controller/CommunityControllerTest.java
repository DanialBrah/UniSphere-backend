package com.unisphere.backend.social.community.controller;

import com.unisphere.backend.social.community.AbstractCommunityIntegrationTest;
import com.unisphere.backend.social.community.dto.request.ChangeRoleRequest;
import com.unisphere.backend.social.community.dto.request.UpdateCommunityRequest;
import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import com.unisphere.backend.social.community.enums.CommunityVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityControllerTest extends AbstractCommunityIntegrationTest {

    // ── Create / read / update / delete ─────────────────────────────────────

    @Test
    void createCommunity_validRequest_returns201AndCreatorIsAdmin() throws Exception {
        String token = registerClubAndGetToken("comm.create@test.com", "Create Club", null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new com.unisphere.backend.social.community.dto.request.CreateCommunityRequest(
                                        "Robotics Club", "For robotics enthusiasts", CommunityVisibility.PUBLIC, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Robotics Club"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.viewerRole").value("ADMIN"));
    }

    @Test
    void getCommunity_privateCommunityAsNonMember_returns200WithBasicInfo() throws Exception {
        // A private community's existence must stay discoverable, or its own join-request
        // workflow has no way to start — see CommunityAccessService.canViewContent javadoc.
        String ownerToken = registerAlumniAndGetToken("comm.private.owner@test.com");
        Long communityId = createCommunity(ownerToken, "Private Circle", CommunityVisibility.PRIVATE);
        String outsiderToken = registerStudentAndGetToken("comm.private.outsider@test.com", "COM1001");

        mockMvc.perform(get(BASE + "/{communityId}", communityId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Private Circle"))
                .andExpect(jsonPath("$.data.viewerRole").doesNotExist());
    }

    @Test
    void getCommunity_nonExisting_returns404() throws Exception {
        String token = registerStudentAndGetToken("comm.notfound@test.com", "COM1002");

        mockMvc.perform(get(BASE + "/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void updateCommunity_asNonAdmin_returns403() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.upd.owner@test.com", "Update Club", null);
        Long communityId = createCommunity(ownerToken, "Chess Club", CommunityVisibility.PUBLIC);
        String memberToken = registerStudentAndGetToken("comm.upd.member@test.com", "COM1003");
        joinCommunity(memberToken, communityId);

        mockMvc.perform(put(BASE + "/{communityId}", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + memberToken)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCommunityRequest("Renamed", null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void updateCommunity_asAdmin_returns200() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.upd.admin@test.com", "Admin Update Club", null);
        Long communityId = createCommunity(ownerToken, "Original Name", CommunityVisibility.PUBLIC);

        mockMvc.perform(put(BASE + "/{communityId}", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCommunityRequest("Renamed Community", "New description", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Community"))
                .andExpect(jsonPath("$.data.description").value("New description"));
    }

    @Test
    void deleteCommunity_asAdmin_returns200AndSubsequentGetIs404() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.del.admin@test.com", "Delete Club", null);
        Long communityId = createCommunity(ownerToken, "To Be Deleted", CommunityVisibility.PUBLIC);

        mockMvc.perform(delete(BASE + "/{communityId}", communityId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/{communityId}", communityId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCommunity_asNonAdmin_returns403() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.del.owner2@test.com", "Delete Club 2", null);
        Long communityId = createCommunity(ownerToken, "Protected Community", CommunityVisibility.PUBLIC);
        String memberToken = registerStudentAndGetToken("comm.del.member2@test.com", "COM1004");
        joinCommunity(memberToken, communityId);

        mockMvc.perform(delete(BASE + "/{communityId}", communityId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void discover_returnsCommunities() throws Exception {
        String token = registerClubAndGetToken("comm.discover@test.com", "Discover Club", null);
        createCommunity(token, "Discoverable Community", CommunityVisibility.PUBLIC);

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));
    }

    // ── Membership ───────────────────────────────────────────────────────────

    @Test
    void join_publicCommunity_incrementsMemberCount() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.join.owner@test.com", "Join Club", null);
        Long communityId = createCommunity(ownerToken, "Open Community", CommunityVisibility.PUBLIC);
        String joinerToken = registerStudentAndGetToken("comm.join.member@test.com", "COM1005");

        mockMvc.perform(post(BASE + "/{communityId}/members", communityId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        mockMvc.perform(get(BASE + "/{communityId}", communityId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data.memberCount").value(2));
    }

    @Test
    void join_privateCommunity_returns400() throws Exception {
        String ownerToken = registerAlumniAndGetToken("comm.join.privowner@test.com");
        Long communityId = createCommunity(ownerToken, "Closed Community", CommunityVisibility.PRIVATE);
        String joinerToken = registerStudentAndGetToken("comm.join.privjoiner@test.com", "COM1006");

        mockMvc.perform(post(BASE + "/{communityId}/members", communityId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leave_asSoleAdmin_returns400() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.leave.sole@test.com", "Sole Admin Club", null);
        Long communityId = createCommunity(ownerToken, "Sole Admin Community", CommunityVisibility.PUBLIC);

        mockMvc.perform(delete(BASE + "/{communityId}/members/me", communityId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leave_asRegularMember_returns200() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.leave.owner@test.com", "Leave Club", null);
        Long communityId = createCommunity(ownerToken, "Leavable Community", CommunityVisibility.PUBLIC);
        String memberToken = registerStudentAndGetToken("comm.leave.member@test.com", "COM1007");
        joinCommunity(memberToken, communityId);

        mockMvc.perform(delete(BASE + "/{communityId}/members/me", communityId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());
    }

    @Test
    void kick_moderatorOnAdmin_returns403() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.kick.owner@test.com", "Kick Club", null);
        Long communityId = createCommunity(ownerToken, "Hierarchy Community", CommunityVisibility.PUBLIC);
        Long ownerId = getUserId(ownerToken);
        String modToken = registerStudentAndGetToken("comm.kick.mod@test.com", "COM1008");
        Long modId = getUserId(modToken);
        joinCommunity(modToken, communityId);
        promoteToModerator(ownerToken, communityId, modId);

        mockMvc.perform(delete(BASE + "/{communityId}/members/{userId}", communityId, ownerId)
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void kick_moderatorOnMember_returns200() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.kick.owner2@test.com", "Kick Club 2", null);
        Long communityId = createCommunity(ownerToken, "Moderated Community", CommunityVisibility.PUBLIC);
        String modToken = registerStudentAndGetToken("comm.kick.mod2@test.com", "COM1009");
        Long modId = getUserId(modToken);
        joinCommunity(modToken, communityId);
        promoteToModerator(ownerToken, communityId, modId);

        String memberToken = registerStudentAndGetToken("comm.kick.member2@test.com", "COM1010");
        Long memberId = getUserId(memberToken);
        joinCommunity(memberToken, communityId);

        mockMvc.perform(delete(BASE + "/{communityId}/members/{userId}", communityId, memberId)
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk());
    }

    // ── Join requests ────────────────────────────────────────────────────────

    @Test
    void joinRequestFlow_approve_grantsMembershipAndChatAccess() throws Exception {
        String ownerToken = registerAlumniAndGetToken("comm.jr.owner@test.com");
        Long communityId = createCommunity(ownerToken, "Mentorship Circle", CommunityVisibility.PRIVATE);
        String requesterToken = registerStudentAndGetToken("comm.jr.requester@test.com", "COM1011");

        Long requestId = requestToJoin(requesterToken, communityId, "Would love to join");

        mockMvc.perform(get(BASE + "/{communityId}/join-requests", communityId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));

        approveJoinRequest(ownerToken, communityId, requestId);

        mockMvc.perform(get(BASE + "/{communityId}/chat", communityId)
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty());
    }

    @Test
    void requestToJoin_publicCommunity_returns400() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.jr.pubowner@test.com", "Public JR Club", null);
        Long communityId = createCommunity(ownerToken, "Open For Direct Join", CommunityVisibility.PUBLIC);
        String requesterToken = registerStudentAndGetToken("comm.jr.pubrequester@test.com", "COM1012");

        mockMvc.perform(post(BASE + "/{communityId}/join-requests", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + requesterToken)
                        .content("{\"message\":\"let me in\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Bans ─────────────────────────────────────────────────────────────────

    @Test
    void ban_blocksSubsequentJoin() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.ban.owner@test.com", "Ban Club", null);
        Long communityId = createCommunity(ownerToken, "Moderated Space", CommunityVisibility.PUBLIC);
        String bannedToken = registerStudentAndGetToken("comm.ban.target@test.com", "COM1013");
        Long bannedUserId = getUserId(bannedToken);

        mockMvc.perform(post(BASE + "/{communityId}/bans/{userId}", communityId, bannedUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"reason\":\"spam\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE + "/{communityId}/members", communityId)
                        .header("Authorization", "Bearer " + bannedToken))
                .andExpect(status().isBadRequest());
    }

    // ── Announcements ────────────────────────────────────────────────────────

    @Test
    void createAnnouncement_asRegularMember_returns403() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.ann.owner@test.com", "Announce Club", null);
        Long communityId = createCommunity(ownerToken, "Announcement Community", CommunityVisibility.PUBLIC);
        String memberToken = registerStudentAndGetToken("comm.ann.member@test.com", "COM1014");
        joinCommunity(memberToken, communityId);

        mockMvc.perform(post(BASE + "/{communityId}/announcements", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + memberToken)
                        .content("{\"title\":\"Hi\",\"content\":\"Hello everyone\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAnnouncement_asAdmin_visibleToNonMemberOfPublicCommunity() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.ann.owner2@test.com", "Announce Club 2", null);
        Long communityId = createCommunity(ownerToken, "Public Announcements", CommunityVisibility.PUBLIC);
        createAnnouncement(ownerToken, communityId, "Welcome", "Read the rules before posting.");

        String outsiderToken = registerStudentAndGetToken("comm.ann.outsider@test.com", "COM1015");
        mockMvc.perform(get(BASE + "/{communityId}/announcements", communityId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Welcome"));
    }

    // ── Community posts ──────────────────────────────────────────────────────

    @Test
    void createCommunityPost_asNonMember_returns403() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.post.owner@test.com", "Post Club", null);
        Long communityId = createCommunity(ownerToken, "Post Community", CommunityVisibility.PUBLIC);
        String outsiderToken = registerStudentAndGetToken("comm.post.outsider@test.com", "COM1016");

        mockMvc.perform(post(BASE + "/{communityId}/posts", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .content(objectMapper.writeValueAsString(
                                new com.unisphere.backend.social.posting.dto.request.CreatePostRequest(
                                        "Title", "Body", null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_COMMUNITY_MEMBER"));
    }

    @Test
    void communityPost_readableInFeedByNonMemberOfPublicCommunity_butNotViaGlobalPostEndpoint() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.post.owner2@test.com", "Post Club 2", null);
        Long communityId = createCommunity(ownerToken, "Feed Community", CommunityVisibility.PUBLIC);
        String memberToken = registerStudentAndGetToken("comm.post.member2@test.com", "COM1017");
        joinCommunity(memberToken, communityId);
        Long postId = createCommunityPost(memberToken, communityId, "Hello community",
                "First post in this community feed.");

        String outsiderToken = registerStudentAndGetToken("comm.post.outsider2@test.com", "COM1018");

        // Public community feed is readable by non-members
        mockMvc.perform(get(BASE + "/{communityId}/posts", communityId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(postId));

        // But the same post is not reachable through the generic, community-unaware posts endpoint
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void removePost_asCommunityModerator_notAuthor_returns200() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.post.owner3@test.com", "Post Club 3", null);
        Long communityId = createCommunity(ownerToken, "Moderated Post Community", CommunityVisibility.PUBLIC);
        String memberToken = registerStudentAndGetToken("comm.post.member3@test.com", "COM1019");
        joinCommunity(memberToken, communityId);
        Long postId = createCommunityPost(memberToken, communityId, "Removable post", "Please remove me");

        mockMvc.perform(delete(BASE + "/{communityId}/posts/{postId}", communityId, postId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    @Test
    void getChatAccess_asNonMember_returns403() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.chat.owner@test.com", "Chat Club", null);
        Long communityId = createCommunity(ownerToken, "Chat Community", CommunityVisibility.PUBLIC);
        String outsiderToken = registerStudentAndGetToken("comm.chat.outsider@test.com", "COM1020");

        mockMvc.perform(get(BASE + "/{communityId}/chat", communityId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_COMMUNITY_MEMBER"));
    }

    @Test
    void directConversationMemberEndpoint_rejectedForCommunityConversation() throws Exception {
        String ownerToken = registerClubAndGetToken("comm.chat.owner2@test.com", "Chat Club 2", null);
        Long communityId = createCommunity(ownerToken, "Guarded Chat Community", CommunityVisibility.PUBLIC);

        MvcResult chatResult = mockMvc.perform(get(BASE + "/{communityId}/chat", communityId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        Long conversationId = Long.parseLong(readJson(chatResult, "/data/conversationId"));

        String otherToken = registerStudentAndGetToken("comm.chat.other2@test.com", "COM1021");
        Long otherUserId = getUserId(otherToken);

        mockMvc.perform(post("/api/v1/conversations/{convId}/members", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"userId\":%d}".formatted(otherUserId)))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void promoteToModerator(String adminToken, Long communityId, Long userId) throws Exception {
        mockMvc.perform(put(BASE + "/{communityId}/members/{userId}/role", communityId, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(CommunityMemberRole.MODERATOR))))
                .andExpect(status().isOk());
    }
}
