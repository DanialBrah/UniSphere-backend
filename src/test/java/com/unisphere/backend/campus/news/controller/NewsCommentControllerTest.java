package com.unisphere.backend.campus.news.controller;

import com.unisphere.backend.campus.news.AbstractNewsIntegrationTest;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NewsCommentControllerTest extends AbstractNewsIntegrationTest {

    @Test
    void commentsCanBeCreatedListedAndReplied() throws Exception {
        String university = registerUniversityAndGetToken("nc.uni.basic@test.com", "Comment University");
        Long articleId = createPublishedArticle(university, "Discussable headline", "Body text here", null);
        String reader = registerStudentAndGetToken("nc.reader@test.com", "NC1001");

        Long commentId = createNewsComment(reader, articleId, "First!");

        mockMvc.perform(get(BASE + "/{articleId}/comments", articleId)
                        .header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].content").value("First!"))
                .andExpect(jsonPath("$.data.content[0].author.displayName").value("Test Student"));

        mockMvc.perform(post(BASE + "/{articleId}/comments", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content("{\"content\":\"Thanks for reading\",\"parentCommentId\":%d}".formatted(commentId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE + "/{articleId}/comments/{commentId}/replies", articleId, commentId)
                        .header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].parentCommentId").value(commentId));

        // Top-level listing still shows one comment — replies are not mixed in.
        mockMvc.perform(get(BASE + "/{articleId}/comments", articleId)
                        .header("Authorization", "Bearer " + reader))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].replyCount").value(1));
    }

    @Test
    void commentingOnADraftIsNotFoundForOthers() throws Exception {
        String university = registerUniversityAndGetToken("nc.uni.draft@test.com", "Draft Comment University");
        Long articleId = createArticle(university, "Unpublished", "Body", NewsCategory.GENERAL,
                NewsVisibility.PUBLIC, NewsStatus.DRAFT);
        String reader = registerStudentAndGetToken("nc.draft.reader@test.com", "NCD1001");

        mockMvc.perform(post(BASE + "/{articleId}/comments", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + reader)
                        .content("{\"content\":\"Sneak peek\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NEWS_ARTICLE_NOT_FOUND"));
    }

    @Test
    void evenTheAuthorCannotCommentOnTheirOwnDraft() throws Exception {
        String university = registerUniversityAndGetToken("nc.uni.own@test.com", "Own Draft University");
        Long articleId = createArticle(university, "Still writing", "Body", NewsCategory.GENERAL,
                NewsVisibility.PUBLIC, NewsStatus.DRAFT);

        mockMvc.perform(post(BASE + "/{articleId}/comments", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content("{\"content\":\"Note to self\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void archivedArticlesAreReadOnly() throws Exception {
        String university = registerUniversityAndGetToken("nc.uni.arch@test.com", "Archived Comment University");
        Long articleId = createPublishedArticle(university, "Closing discussion", "Body text here", null);
        String reader = registerStudentAndGetToken("nc.arch.reader@test.com", "NCA1001");
        createNewsComment(reader, articleId, "Before archiving");

        changeStatus(university, articleId, NewsStatus.ARCHIVED);

        mockMvc.perform(post(BASE + "/{articleId}/comments", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + reader)
                        .content("{\"content\":\"After archiving\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        // Existing discussion stays readable.
        mockMvc.perform(get(BASE + "/{articleId}/comments", articleId)
                        .header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void onlyTheOwnerCanEditAndOwnerOrAdminCanDelete() throws Exception {
        String university = registerUniversityAndGetToken("nc.uni.perm@test.com", "Perm University");
        Long articleId = createPublishedArticle(university, "Moderated headline", "Body text here", null);
        String owner = registerStudentAndGetToken("nc.owner@test.com", "NCO1001");
        String other = registerStudentAndGetToken("nc.other@test.com", "NCT1001");
        Long commentId = createNewsComment(owner, articleId, "Mine");

        mockMvc.perform(put(BASE + "/{articleId}/comments/{commentId}", articleId, commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content("{\"content\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE + "/{articleId}/comments/{commentId}", articleId, commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + owner)
                        .content("{\"content\":\"Edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Edited"));

        mockMvc.perform(delete(BASE + "/{articleId}/comments/{commentId}", articleId, commentId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE + "/{articleId}/comments/{commentId}", articleId, commentId)
                        .header("Authorization", "Bearer " + registerAdminAndGetToken("nc.admin@test.com")))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/{articleId}/comments", articleId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void commentLikeToggles() throws Exception {
        String university = registerUniversityAndGetToken("nc.uni.like@test.com", "Like Comment University");
        Long articleId = createPublishedArticle(university, "Likeable discussion", "Body text here", null);
        String reader = registerStudentAndGetToken("nc.like.reader@test.com", "NCL1001");
        Long commentId = createNewsComment(reader, articleId, "Worth a like");
        String path = BASE + "/{articleId}/comments/{commentId}/like";

        mockMvc.perform(post(path, articleId, commentId).header("Authorization", "Bearer " + university))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likesCount").value(1));
        mockMvc.perform(post(path, articleId, commentId).header("Authorization", "Bearer " + university))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likesCount").value(0));
    }

    @Test
    void blankCommentFailsValidation() throws Exception {
        String university = registerUniversityAndGetToken("nc.uni.valid@test.com", "Valid Comment University");
        Long articleId = createPublishedArticle(university, "Validated headline", "Body text here", null);

        mockMvc.perform(post(BASE + "/{articleId}/comments", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
