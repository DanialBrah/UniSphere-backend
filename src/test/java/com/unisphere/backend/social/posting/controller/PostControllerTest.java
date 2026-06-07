package com.unisphere.backend.social.posting.controller;

import com.unisphere.backend.social.posting.AbstractPostingIntegrationTest;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.dto.request.UpdatePostRequest;
import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PostControllerTest extends AbstractPostingIntegrationTest {

    private static final String BASE = "/api/v1/posts";

    // ── Create post ───────────────────────────────────────────────────────────

    @Test
    void createPost_validTextPost_returns201() throws Exception {
        String token = registerStudentAndGetToken("post.create@test.com", "MAT1001");

        CreatePostRequest req = new CreatePostRequest(
                "My First Post", "Hello UniSphere!", PostType.TEXT, PostVisibility.PUBLIC,
                null, null, null
        );

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.content").value("Hello UniSphere!"))
                .andExpect(jsonPath("$.data.postType").value("TEXT"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.saved").value(false))
                .andExpect(jsonPath("$.data.author.role").value("STUDENT"));
    }

    @Test
    void createPost_withoutAuth_returns401() throws Exception {
        CreatePostRequest req = new CreatePostRequest(
                "No Auth", "Should fail", PostType.TEXT, PostVisibility.PUBLIC,
                null, null, null
        );

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPost_imageTypeWithMedia_returns201() throws Exception {
        String token = registerStudentAndGetToken("post.media@test.com", "MAT1002");

        CreatePostRequest.MediaItem mediaItem = new CreatePostRequest.MediaItem("posts/1/photo.jpg", "image/jpeg");
        CreatePostRequest req = new CreatePostRequest(
                "Photo Post", "Check out this photo!", PostType.IMAGE, PostVisibility.PUBLIC,
                null, null, java.util.List.of(mediaItem)
        );

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postType").value("IMAGE"))
                .andExpect(jsonPath("$.data.media[0].mediaUrl").value("posts/1/photo.jpg"));
    }

    // ── Feed ─────────────────────────────────────────────────────────────────

    @Test
    void getFeed_withPosts_returnsPaginatedResults() throws Exception {
        String token = registerStudentAndGetToken("feed.test@test.com", "MAT1003");
        createTextPost(token, "Feed post content");

        mockMvc.perform(get(BASE + "?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].content").value("Feed post content"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getFeed_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    // ── Get single post ───────────────────────────────────────────────────────

    @Test
    void getPost_existingId_returns200() throws Exception {
        String token = registerStudentAndGetToken("post.get@test.com", "MAT1004");
        Long postId = createTextPost(token, "Single post content");

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(postId))
                .andExpect(jsonPath("$.data.content").value("Single post content"));
    }

    @Test
    void getPost_nonExistingId_returns404() throws Exception {
        String token = registerStudentAndGetToken("post.notfound@test.com", "MAT1005");

        mockMvc.perform(get(BASE + "/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    // ── Get posts by user ─────────────────────────────────────────────────────

    @Test
    void getPostsByUser_returns200WithUsersPosts() throws Exception {
        String token = registerStudentAndGetToken("posts.byuser@test.com", "MAT1006");
        Long postId = createTextPost(token, "User's post");

        Long userId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/auth/me")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .at("/data/id").asLong();

        mockMvc.perform(get(BASE + "/user/{userId}?page=0&size=10", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(postId));
    }

    // ── Update post ───────────────────────────────────────────────────────────

    @Test
    void updatePost_asOwner_returns200WithNewContent() throws Exception {
        String token = registerStudentAndGetToken("post.update@test.com", "MAT1007");
        Long postId = createTextPost(token, "Original content");

        UpdatePostRequest update = new UpdatePostRequest("Updated title", "Updated content", PostVisibility.FRIENDS);

        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Updated content"))
                .andExpect(jsonPath("$.data.visibility").value("FRIENDS"));
    }

    @Test
    void updatePost_asNonOwner_returns403() throws Exception {
        String ownerToken  = registerStudentAndGetToken("post.owner@test.com", "MAT1008");
        String otherToken  = registerStudentAndGetToken("post.other@test.com", "MAT1009");
        Long postId = createTextPost(ownerToken, "Owner's post");

        UpdatePostRequest update = new UpdatePostRequest(null, "Hacked!", null);

        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    // ── Delete post ───────────────────────────────────────────────────────────

    @Test
    void deletePost_asOwner_softDeletesThenReturns404() throws Exception {
        String token = registerStudentAndGetToken("post.delete@test.com", "MAT1010");
        Long postId = createTextPost(token, "To be deleted");

        mockMvc.perform(delete(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Soft-deleted post must no longer be visible
        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePost_asNonOwner_returns403() throws Exception {
        String ownerToken = registerStudentAndGetToken("del.owner@test.com", "MAT1011");
        String otherToken = registerStudentAndGetToken("del.other@test.com", "MAT1012");
        Long postId = createTextPost(ownerToken, "Protected post");

        mockMvc.perform(delete(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // ── Like ─────────────────────────────────────────────────────────────────

    @Test
    void toggleLike_firstCall_returnsLiked() throws Exception {
        String token = registerStudentAndGetToken("like.first@test.com", "MAT1013");
        Long postId = createTextPost(token, "Likeable post");

        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likesCount").value(1));
    }

    @Test
    void toggleLike_secondCall_returnsUnliked() throws Exception {
        String token = registerStudentAndGetToken("like.toggle@test.com", "MAT1014");
        Long postId = createTextPost(token, "Toggle like post");

        // Like
        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                .header("Authorization", "Bearer " + token));

        // Unlike
        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likesCount").value(0));
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    void toggleSave_firstCall_returnsSaved() throws Exception {
        String token = registerStudentAndGetToken("save.first@test.com", "MAT1015");
        Long postId = createTextPost(token, "Saveable post");

        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));
    }

    @Test
    void toggleSave_secondCall_returnsUnsaved() throws Exception {
        String token = registerStudentAndGetToken("save.toggle@test.com", "MAT1016");
        Long postId = createTextPost(token, "Toggle save post");

        // Save
        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                .header("Authorization", "Bearer " + token));

        // Unsave
        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    void searchPosts_matchingQuery_returns200() throws Exception {
        String token = registerStudentAndGetToken("search.test@test.com", "MAT1017");
        createTextPost(token, "UniSphere campus app is amazing");

        mockMvc.perform(get(BASE + "/search?q=UniSphere")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
