package com.unisphere.backend.social.posting.controller;

import com.unisphere.backend.social.posting.AbstractPostingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class CommentControllerTest extends AbstractPostingIntegrationTest {

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
}
