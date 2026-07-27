package com.unisphere.backend.social.posting.controller;

import com.unisphere.backend.identity.dto.LoginRequest;
import com.unisphere.backend.identity.entity.Admin;
import com.unisphere.backend.identity.entity.UserStatus;
import com.unisphere.backend.identity.repository.AdminRepository;
import com.unisphere.backend.social.posting.AbstractPostingIntegrationTest;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class CommentControllerTest extends AbstractPostingIntegrationTest {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_PASSWORD = "Password123!";

    private String registerAdminAndGetToken(String email) throws Exception {
        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setVerified(true);
        admin.setFullName("Test Admin");
        adminRepository.save(admin);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    private String commentsUrl(Long postId) {
        return "/api/v1/posts/" + postId + "/comments";
    }

    // ── Create comment ────────────────────────────────────────────────────────

    @Test
    void createComment_validContent_returns201() throws Exception {
        String token = registerStudentAndGetToken("comment.create@test.com", "MAT2001");
        Long postId = createTextPost(token, "Post to comment on");

        mockMvc.perform(post(commentsUrl(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"content\":\"Great post!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.content").value("Great post!"))
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.parentCommentId").doesNotExist())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.author.role").value("STUDENT"));
    }

    @Test
    void createComment_withoutAuth_returns401() throws Exception {
        String token = registerStudentAndGetToken("comment.noauth@test.com", "MAT2002");
        Long postId = createTextPost(token, "Post");

        mockMvc.perform(post(commentsUrl(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"No token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createComment_blankContent_returns400() throws Exception {
        String token = registerStudentAndGetToken("comment.blank@test.com", "MAT2003");
        Long postId = createTextPost(token, "Post");

        mockMvc.perform(post(commentsUrl(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createComment_onNonExistentPost_returns404() throws Exception {
        String token = registerStudentAndGetToken("comment.nopost@test.com", "MAT2004");

        mockMvc.perform(post("/api/v1/posts/999999/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"content\":\"Commenting on ghost post\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    // ── Replies ───────────────────────────────────────────────────────────────

    @Test
    void createReply_withValidParentId_returns201WithParentSet() throws Exception {
        String token = registerStudentAndGetToken("reply.create@test.com", "MAT2005");
        Long postId = createTextPost(token, "Post for threading");
        Long parentId = createComment(token, postId, "Parent comment");

        mockMvc.perform(post(commentsUrl(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"content\":\"Reply!\",\"parentCommentId\":" + parentId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentCommentId").value(parentId));
    }

    // ── Get comments ──────────────────────────────────────────────────────────

    @Test
    void getComments_returnsOnlyTopLevel() throws Exception {
        String token = registerStudentAndGetToken("comments.get@test.com", "MAT2006");
        Long postId = createTextPost(token, "Post with comments");
        Long parentId = createComment(token, postId, "Top level comment");
        createComment(token, postId, "Another top level");
        // add a reply — should NOT appear in top-level list
        mockMvc.perform(post(commentsUrl(postId))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"content\":\"Reply\",\"parentCommentId\":" + parentId + "}"));

        mockMvc.perform(get(commentsUrl(postId) + "?page=0&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getReplies_returnsChildComments() throws Exception {
        String token = registerStudentAndGetToken("replies.get@test.com", "MAT2007");
        Long postId = createTextPost(token, "Post");
        Long parentId = createComment(token, postId, "Parent");
        mockMvc.perform(post(commentsUrl(postId))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"content\":\"Reply 1\",\"parentCommentId\":" + parentId + "}"));
        mockMvc.perform(post(commentsUrl(postId))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"content\":\"Reply 2\",\"parentCommentId\":" + parentId + "}"));

        mockMvc.perform(get(commentsUrl(postId) + "/" + parentId + "/replies?page=0&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    // ── Update comment ────────────────────────────────────────────────────────

    @Test
    void updateComment_asOwner_returns200WithNewContent() throws Exception {
        String token = registerStudentAndGetToken("comment.update@test.com", "MAT2008");
        Long postId = createTextPost(token, "Post");
        Long commentId = createComment(token, postId, "Original comment");

        mockMvc.perform(put(commentsUrl(postId) + "/" + commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"content\":\"Edited comment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Edited comment"));
    }

    @Test
    void updateComment_asNonOwner_returns403() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.owner@test.com", "MAT2009");
        String otherToken = registerStudentAndGetToken("comment.other@test.com", "MAT2010");
        Long postId = createTextPost(ownerToken, "Post");
        Long commentId = createComment(ownerToken, postId, "Owner's comment");

        mockMvc.perform(put(commentsUrl(postId) + "/" + commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken)
                        .content("{\"content\":\"Hacked!\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Delete comment ────────────────────────────────────────────────────────

    @Test
    void deleteComment_asOwner_softDeletesAndDisappearsFromList() throws Exception {
        String token = registerStudentAndGetToken("comment.delete@test.com", "MAT2011");
        Long postId = createTextPost(token, "Post");
        Long commentId = createComment(token, postId, "To be deleted");

        mockMvc.perform(delete(commentsUrl(postId) + "/" + commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Deleted comment must not appear in the list
        mockMvc.perform(get(commentsUrl(postId) + "?page=0&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── Like comment ──────────────────────────────────────────────────────────

    @Test
    void toggleLike_firstCall_returnsLiked() throws Exception {
        String token = registerStudentAndGetToken("comment.like@test.com", "MAT2012");
        Long postId = createTextPost(token, "Post");
        Long commentId = createComment(token, postId, "Likeable comment");

        mockMvc.perform(post(commentsUrl(postId) + "/" + commentId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likesCount").value(1));
    }

    @Test
    void toggleLike_secondCall_returnsUnliked() throws Exception {
        String token = registerStudentAndGetToken("comment.unlike@test.com", "MAT2013");
        Long postId = createTextPost(token, "Post");
        Long commentId = createComment(token, postId, "Toggle like");

        // Like
        mockMvc.perform(post(commentsUrl(postId) + "/" + commentId + "/like")
                .header("Authorization", "Bearer " + token));
        // Unlike
        mockMvc.perform(post(commentsUrl(postId) + "/" + commentId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likesCount").value(0));
    }

    // ── Visibility enforcement (inherited from the parent post) ────────────────

    @Test
    void createComment_onPrivatePost_asNonViewer_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.vis.create.owner@test.com", "MAT2014");
        String otherToken = registerStudentAndGetToken("comment.vis.create.other@test.com", "MAT2015");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);

        mockMvc.perform(post(commentsUrl(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken)
                        .content("{\"content\":\"Should not be allowed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComments_onPrivatePost_asNonViewer_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.vis.get.owner@test.com", "MAT2016");
        String otherToken = registerStudentAndGetToken("comment.vis.get.other@test.com", "MAT2017");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        createComment(ownerToken, postId, "Owner's comment");

        mockMvc.perform(get(commentsUrl(postId) + "?page=0&size=20")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReplies_onPrivatePost_asNonViewer_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.vis.replies.owner@test.com", "MAT2018");
        String otherToken = registerStudentAndGetToken("comment.vis.replies.other@test.com", "MAT2019");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        Long parentId = createComment(ownerToken, postId, "Owner's parent comment");

        mockMvc.perform(get(commentsUrl(postId) + "/" + parentId + "/replies?page=0&size=20")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReplies_nonExistentCommentId_returns404() throws Exception {
        String token = registerStudentAndGetToken("comment.vis.noreply@test.com", "MAT2020");
        Long postId = createTextPost(token, "Post");

        mockMvc.perform(get(commentsUrl(postId) + "/999999/replies?page=0&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateComment_onPrivatePost_asNonViewer_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.vis.update.owner@test.com", "MAT2021");
        String otherToken = registerStudentAndGetToken("comment.vis.update.other@test.com", "MAT2022");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        Long commentId = createComment(ownerToken, postId, "Owner's comment");

        mockMvc.perform(put(commentsUrl(postId) + "/" + commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken)
                        .content("{\"content\":\"Hacked!\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteComment_onPrivatePost_asNonViewer_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.vis.delete.owner@test.com", "MAT2023");
        String otherToken = registerStudentAndGetToken("comment.vis.delete.other@test.com", "MAT2024");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        Long commentId = createComment(ownerToken, postId, "Owner's comment");

        mockMvc.perform(delete(commentsUrl(postId) + "/" + commentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleLike_onCommentOfPrivatePost_asNonViewer_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.vis.like.owner@test.com", "MAT2025");
        String otherToken = registerStudentAndGetToken("comment.vis.like.other@test.com", "MAT2026");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        Long commentId = createComment(ownerToken, postId, "Owner's comment");

        mockMvc.perform(post(commentsUrl(postId) + "/" + commentId + "/like")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteComment_asAdmin_onOthersPrivatePost_returns200() throws Exception {
        String ownerToken = registerStudentAndGetToken("comment.admin.delete.owner@test.com", "MAT2027");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        Long commentId = createComment(ownerToken, postId, "Owner's comment");
        String adminToken = registerAdminAndGetToken("comment.admin.delete@test.com");

        mockMvc.perform(delete(commentsUrl(postId) + "/" + commentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
