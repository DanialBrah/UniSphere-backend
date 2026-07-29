package com.unisphere.backend.social.follow.controller;

import com.unisphere.backend.social.posting.AbstractPostingIntegrationTest;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FollowControllerTest extends AbstractPostingIntegrationTest {

    private Long getUserId(String token) throws Exception {
        return objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/auth/me")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .at("/data/id").asLong();
    }

    private String followUrl(Long userId) {
        return "/api/v1/users/" + userId + "/follow";
    }

    @Test
    void toggleFollow_firstCall_returnsFollowingTrue() throws Exception {
        String followerToken = registerStudentAndGetToken("follow.first.follower@test.com", "MAT3001");
        String followingToken = registerStudentAndGetToken("follow.first.following@test.com", "MAT3002");
        Long followingId = getUserId(followingToken);

        mockMvc.perform(post(followUrl(followingId))
                        .header("Authorization", "Bearer " + followerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.followersCount").value(1));
    }

    @Test
    void toggleFollow_secondCall_returnsFollowingFalse() throws Exception {
        String followerToken = registerStudentAndGetToken("follow.second.follower@test.com", "MAT3003");
        String followingToken = registerStudentAndGetToken("follow.second.following@test.com", "MAT3004");
        Long followingId = getUserId(followingToken);

        mockMvc.perform(post(followUrl(followingId))
                .header("Authorization", "Bearer " + followerToken));

        mockMvc.perform(post(followUrl(followingId))
                        .header("Authorization", "Bearer " + followerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false))
                .andExpect(jsonPath("$.data.followersCount").value(0));
    }

    @Test
    void toggleFollow_self_returns400() throws Exception {
        String token = registerStudentAndGetToken("follow.self@test.com", "MAT3005");
        Long userId = getUserId(token);

        mockMvc.perform(post(followUrl(userId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toggleFollow_nonExistentUser_returns404() throws Exception {
        String token = registerStudentAndGetToken("follow.ghost@test.com", "MAT3006");

        mockMvc.perform(post(followUrl(999999L))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleFollow_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post(followUrl(1L)))
                .andExpect(status().isUnauthorized());
    }

    // ── Follow notification ──────────────────────────────────────────────────
    // Regression: follow notifications rendered as "Someone sent you a notification" and, when
    // clicked, navigated to /post/{targetId} — landing on an unrelated post, because targetId on
    // a FOLLOW is a user id. Both depend on targetType and actorName being correct here.

    @Test
    void follow_createsNotification_withUserTargetTypeAndActorName() throws Exception {
        String actorToken = registerStudentAndGetToken("notif.actor@test.com", "MAT3601");
        String targetToken = registerStudentAndGetToken("notif.target@test.com", "MAT3602");
        Long actorId = getUserId(actorToken);
        Long targetId = getUserId(targetToken);

        mockMvc.perform(post(followUrl(targetId)).header("Authorization", "Bearer " + actorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].notifType").value("FOLLOW"))
                // targetType drives navigation — USER means "go to a profile", not a post
                .andExpect(jsonPath("$.data.content[0].targetType").value("USER"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(actorId))
                .andExpect(jsonPath("$.data.content[0].actorId").value(actorId))
                // actorName is what replaces the generic "Someone" in the UI
                .andExpect(jsonPath("$.data.content[0].actorName").value("Test User"));
    }

    @Test
    void likeNotification_stillCarriesPostTargetType() throws Exception {
        // Guards the other half of the routing fix: POST-targeted types must keep working.
        String ownerToken = registerStudentAndGetToken("notif.post.owner@test.com", "MAT3701");
        String likerToken = registerStudentAndGetToken("notif.post.liker@test.com", "MAT3702");

        CreatePostRequest req = new CreatePostRequest(
                "Notif post", "body", PostType.TEXT, PostVisibility.PUBLIC, null, null, null);
        String created = mockMvc.perform(post("/api/v1/posts")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        Long postId = objectMapper.readTree(created).at("/data/id").asLong();

        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + likerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].notifType").value("LIKE"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("POST"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(postId))
                .andExpect(jsonPath("$.data.content[0].actorName").isNotEmpty());
    }

    // ── Follow stats ─────────────────────────────────────────────────────────

    @Test
    void followStats_reflectsBothDirectionsAndViewerRelationship() throws Exception {
        String aToken = registerStudentAndGetToken("stats.a@test.com", "MAT3101");
        String bToken = registerStudentAndGetToken("stats.b@test.com", "MAT3102");
        String cToken = registerStudentAndGetToken("stats.c@test.com", "MAT3103");
        Long bId = getUserId(bToken);
        Long cId = getUserId(cToken);

        // A follows B, C follows B, B follows C
        mockMvc.perform(post(followUrl(bId)).header("Authorization", "Bearer " + aToken));
        mockMvc.perform(post(followUrl(bId)).header("Authorization", "Bearer " + cToken));
        mockMvc.perform(post(followUrl(cId)).header("Authorization", "Bearer " + bToken));

        mockMvc.perform(get("/api/v1/users/" + bId + "/follow-stats")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followersCount").value(2))
                .andExpect(jsonPath("$.data.followingCount").value(1))
                // isFollowing is relative to the requester — A does follow B
                .andExpect(jsonPath("$.data.isFollowing").value(true));
    }

    @Test
    void followStats_isFollowingFalse_whenViewerDoesNotFollow() throws Exception {
        String viewerToken = registerStudentAndGetToken("stats.viewer@test.com", "MAT3104");
        String targetToken = registerStudentAndGetToken("stats.target@test.com", "MAT3105");
        Long targetId = getUserId(targetToken);

        mockMvc.perform(get("/api/v1/users/" + targetId + "/follow-stats")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followersCount").value(0))
                .andExpect(jsonPath("$.data.isFollowing").value(false));
    }

    // ── Followers / following lists ──────────────────────────────────────────

    @Test
    void followersAndFollowing_listTheRightPeople() throws Exception {
        String aToken = registerStudentAndGetToken("list.a@test.com", "MAT3201");
        String bToken = registerStudentAndGetToken("list.b@test.com", "MAT3202");
        Long aId = getUserId(aToken);
        Long bId = getUserId(bToken);

        mockMvc.perform(post(followUrl(bId)).header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk());

        // B's followers contains A
        mockMvc.perform(get("/api/v1/users/" + bId + "/followers")
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(aId));

        // A's following contains B, and is marked as followed from A's perspective
        mockMvc.perform(get("/api/v1/users/" + aId + "/following")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(bId))
                .andExpect(jsonPath("$.data.content[0].isFollowing").value(true));
    }

    // ── isFollowing on search results ────────────────────────────────────────

    @Test
    void search_marksAlreadyFollowedUsers() throws Exception {
        String meToken = registerStudentAndGetToken("srch.me@test.com", "MAT3301");
        String targetToken = registerStudentAndGetToken("srch.target@test.com", "MAT3302");
        Long targetId = getUserId(targetToken);

        mockMvc.perform(get("/api/v1/users/search?q=srch.target")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(targetId))
                .andExpect(jsonPath("$.data.content[0].isFollowing").value(false));

        mockMvc.perform(post(followUrl(targetId)).header("Authorization", "Bearer " + meToken));

        mockMvc.perform(get("/api/v1/users/search?q=srch.target")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].isFollowing").value(true));
    }

    // ── Recommendations ──────────────────────────────────────────────────────

    @Test
    void recommendations_rankFriendsOfFriendsFirst_andExcludeAlreadyFollowed() throws Exception {
        // me -> bridge -> target.  target should surface as a friend-of-friend.
        String meToken     = registerStudentAndGetToken("rec.me@test.com", "MAT3401");
        String bridgeToken = registerStudentAndGetToken("rec.bridge@test.com", "MAT3402");
        String targetToken = registerStudentAndGetToken("rec.target@test.com", "MAT3403");
        Long bridgeId = getUserId(bridgeToken);
        Long targetId = getUserId(targetToken);

        mockMvc.perform(post(followUrl(bridgeId)).header("Authorization", "Bearer " + meToken));
        mockMvc.perform(post(followUrl(targetId)).header("Authorization", "Bearer " + bridgeToken));

        mockMvc.perform(get("/api/v1/users/recommendations")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                // friend-of-friend ranks first
                .andExpect(jsonPath("$.data[0].id").value(targetId))
                .andExpect(jsonPath("$.data[0].isFollowing").value(false));

        // Once followed, they drop out of recommendations entirely
        mockMvc.perform(post(followUrl(targetId)).header("Authorization", "Bearer " + meToken));

        mockMvc.perform(get("/api/v1/users/recommendations")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + targetId + ")]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.id == " + bridgeId + ")]").isEmpty());
    }

    @Test
    void recommendations_neverIncludeSelf_andRespectLimit() throws Exception {
        String meToken = registerStudentAndGetToken("rec.solo@test.com", "MAT3501");
        Long meId = getUserId(meToken);

        mockMvc.perform(get("/api/v1/users/recommendations?limit=5")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.id == " + meId + ")]").isEmpty())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(5)));
    }

    @Test
    void recommendations_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mutualFollow_makesFriendsVisibilityPostReachable() throws Exception {
        String ownerToken = registerStudentAndGetToken("follow.mutual.owner@test.com", "MAT3007");
        String otherToken = registerStudentAndGetToken("follow.mutual.other@test.com", "MAT3008");
        Long ownerId = getUserId(ownerToken);
        Long otherId = getUserId(otherToken);

        CreatePostRequest req = new CreatePostRequest(
                "Friends post", "Only for friends", PostType.TEXT, PostVisibility.FRIENDS,
                null, null, null);
        String createResponse = mockMvc.perform(post("/api/v1/posts")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        Long postId = objectMapper.readTree(createResponse).at("/data/id").asLong();

        // Not yet mutual — invisible
        mockMvc.perform(get("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // Follow both directions via the real API
        mockMvc.perform(post(followUrl(ownerId)).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
        mockMvc.perform(post(followUrl(otherId)).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Now mutual — visible
        mockMvc.perform(get("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId));
    }
}
