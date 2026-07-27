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
