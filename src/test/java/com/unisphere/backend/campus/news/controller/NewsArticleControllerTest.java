package com.unisphere.backend.campus.news.controller;

import com.unisphere.backend.campus.news.AbstractNewsIntegrationTest;
import com.unisphere.backend.campus.news.dto.request.CreateNewsArticleRequest;
import com.unisphere.backend.campus.news.dto.request.NewsStatusUpdateRequest;
import com.unisphere.backend.campus.news.dto.request.UpdateNewsArticleRequest;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NewsArticleControllerTest extends AbstractNewsIntegrationTest {

    // ── Who may author ────────────────────────────────────────────────────────

    @Test
    void studentCannotAuthorNews() throws Exception {
        String student = registerStudentAndGetToken("news.student@test.com", "NS1001");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + student)
                        .content(objectMapper.writeValueAsString(new CreateNewsArticleRequest(
                                "Nope", null, "Body", null, null, null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NEWS_AUTHORING_NOT_ALLOWED"));
    }

    @Test
    void alumniCannotAuthorNews() throws Exception {
        String alumni = registerAlumniAndGetToken("news.alumni@test.com");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + alumni)
                        .content(objectMapper.writeValueAsString(new CreateNewsArticleRequest(
                                "Nope", null, "Body", null, null, null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NEWS_AUTHORING_NOT_ALLOWED"));
    }

    @Test
    void universityEmployerClubAndAdminCanAuthorNews() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.author@test.com", "Author University");
        Long universityId = getUserId(university);

        createArticle(university, "By university", "Body", NewsCategory.ACADEMIC,
                NewsVisibility.PUBLIC, NewsStatus.DRAFT);
        createArticle(registerEmployerAndGetToken("news.emp.author@test.com", "Acme"),
                "By employer", "Body", NewsCategory.TECH, NewsVisibility.PUBLIC, NewsStatus.DRAFT);
        createArticle(registerClubAndGetToken("news.club.author@test.com", "Chess Club", universityId),
                "By club", "Body", NewsCategory.EVENTS, NewsVisibility.PUBLIC, NewsStatus.DRAFT);
        createArticle(registerAdminAndGetToken("news.admin.author@test.com"),
                "By admin", "Body", NewsCategory.GENERAL, NewsVisibility.PUBLIC, NewsStatus.DRAFT);
    }

    // ── University scope is derived server-side ───────────────────────────────

    @Test
    void clubArticleInheritsItsUniversityId() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.scope@test.com", "Scope University");
        Long universityId = getUserId(university);
        String club = registerClubAndGetToken("news.club.scope@test.com", "Scoped Club", universityId);

        Long articleId = createArticle(club, "Club news", "Body", NewsCategory.GENERAL,
                NewsVisibility.UNIVERSITY, NewsStatus.PUBLISHED);

        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + club))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.universityId").value(universityId));
    }

    @Test
    void universityAuthorScopesToItself() throws Exception {
        // Regression guard: UserService.universityIdOf returns null for a University account, so
        // using it here would leave universityId null and hide the article from everyone.
        String university = registerUniversityAndGetToken("news.uni.self@test.com", "Self University");
        Long universityId = getUserId(university);

        Long articleId = createArticle(university, "Own news", "Body", NewsCategory.GENERAL,
                NewsVisibility.UNIVERSITY, NewsStatus.PUBLISHED);

        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + university))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.universityId").value(universityId));
    }

    @Test
    void employerCannotCreateUniversityScopedArticle() throws Exception {
        String employer = registerEmployerAndGetToken("news.emp.scope@test.com", "Unaffiliated Ltd");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + employer)
                        .content(objectMapper.writeValueAsString(new CreateNewsArticleRequest(
                                "No affiliation", null, "Body", null, NewsVisibility.UNIVERSITY,
                                null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void universityScopedArticleIsHiddenFromOutsiders() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.wall@test.com", "Walled University");
        Long universityId = getUserId(university);

        Long articleId = createArticle(university, "Members only", "Body", NewsCategory.GENERAL,
                NewsVisibility.UNIVERSITY, NewsStatus.PUBLISHED);

        String insider = registerStudentAndGetToken("news.insider@test.com", "NI1001");
        setStudentUniversityId(getUserId(insider), universityId);
        String outsider = registerStudentAndGetToken("news.outsider@test.com", "NO1001");
        String employer = registerEmployerAndGetToken("news.emp.wall@test.com", "Outside Ltd");
        String admin = registerAdminAndGetToken("news.admin.wall@test.com");

        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + insider))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + employer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    // ── Draft visibility ──────────────────────────────────────────────────────

    @Test
    void draftIs404ForOthersButVisibleToAuthorAndAdmin() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.draft@test.com", "Draft University");
        Long articleId = createArticle(university, "Unfinished", "Body", NewsCategory.GENERAL,
                NewsVisibility.PUBLIC, NewsStatus.DRAFT);

        String reader = registerStudentAndGetToken("news.draft.reader@test.com", "ND1001");

        // 404 rather than 403 — a 403 would confirm the draft exists.
        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + reader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NEWS_ARTICLE_NOT_FOUND"));
        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + university))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/{id}", articleId)
                        .header("Authorization", "Bearer " + registerAdminAndGetToken("news.admin.draft@test.com")))
                .andExpect(status().isOk());
    }

    // ── Status machine ────────────────────────────────────────────────────────

    @Test
    void publishingSetsPublishedAt() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.pub@test.com", "Publish University");
        Long articleId = createArticle(university, "Soon live", "Body", NewsCategory.GENERAL,
                NewsVisibility.PUBLIC, NewsStatus.DRAFT);

        mockMvc.perform(patch(BASE + "/{id}/status", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(
                                new NewsStatusUpdateRequest(NewsStatus.PUBLISHED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
    }

    @Test
    void publishingWithBlankContentIsRejected() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.blank@test.com", "Blank University");
        Long articleId = createArticle(university, new CreateNewsArticleRequest(
                "Title only", null, "   ", null, null, NewsStatus.DRAFT, null, null, null, null));

        mockMvc.perform(patch(BASE + "/{id}/status", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(
                                new NewsStatusUpdateRequest(NewsStatus.PUBLISHED, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void archivedLeavesTheFeedButStaysReadableByLink() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.arch@test.com", "Archive University");
        Long articleId = createPublishedArticle(university, "Archivable headline", "Body text here", null);
        String reader = registerStudentAndGetToken("news.arch.reader@test.com", "NA1001");

        mockMvc.perform(get(BASE + "?size=100").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(articleId.intValue())));

        changeStatus(university, articleId, NewsStatus.ARCHIVED);

        mockMvc.perform(get(BASE + "?size=100").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(articleId.intValue()))));
        // A permalink that 404s would punish readers for an editorial action.
        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
    }

    @Test
    void archivedCannotGoBackToDraft() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.a2d@test.com", "A2D University");
        Long articleId = createPublishedArticle(university, "Archived then draft", "Body text here", null);
        changeStatus(university, articleId, NewsStatus.ARCHIVED);

        mockMvc.perform(patch(BASE + "/{id}/status", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(
                                new NewsStatusUpdateRequest(NewsStatus.DRAFT, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_NEWS_STATUS_TRANSITION"));
    }

    @Test
    void creatingDirectlyAsArchivedIsRejected() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.newarch@test.com", "NewArch University");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(new CreateNewsArticleRequest(
                                "Born archived", null, "Body", null, null, NewsStatus.ARCHIVED,
                                null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_NEWS_STATUS_TRANSITION"));
    }

    // ── Filters, search, tags ─────────────────────────────────────────────────

    @Test
    void feedFiltersByCategoryAndTag() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.filter@test.com", "Filter University");
        Long sports = createArticle(university, new CreateNewsArticleRequest(
                "Sports day results", "s", "Body text here", NewsCategory.SPORTS,
                NewsVisibility.PUBLIC, NewsStatus.PUBLISHED, null, null, List.of("varsity"), null));
        createArticle(university, new CreateNewsArticleRequest(
                "Exam timetable", "s", "Body text here", NewsCategory.ACADEMIC,
                NewsVisibility.PUBLIC, NewsStatus.PUBLISHED, null, null, List.of("exams"), null));

        mockMvc.perform(get(BASE + "?category=SPORTS&size=100")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].category", everyItem(is("SPORTS"))))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(sports.intValue())));

        mockMvc.perform(get(BASE + "?tag=varsity&size=100")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(sports.intValue())))
                .andExpect(jsonPath("$.data.content[*].tags[*]", hasItem("varsity")));
    }

    @Test
    void featuredRouteResolvesAsALiteralNotAnArticleId() throws Exception {
        // Regression guard: a typo'd literal mapping falls through to /{articleId} and yields a
        // 500 from MethodArgumentTypeMismatchException rather than a 404.
        String reader = registerStudentAndGetToken("news.featured@test.com", "NF1001");

        mockMvc.perform(get(BASE + "/featured").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/tags").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/me").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/liked").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/saved").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
    }

    @Test
    void searchFindsPublishedArticlesAndSurvivesOperatorCharacters() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.search@test.com", "Search University");
        Long articleId = createPublishedArticle(university,
                "Convocation ceremony announced", "The convocation will be held in the main hall", null);

        mockMvc.perform(get(BASE + "/search?q=convocation")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(articleId.intValue())));

        mockMvc.perform(get(BASE + "/search?q=zzzznothingmatches")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        // Unsanitized, these are FULLTEXT BOOLEAN MODE operators and raise a MySQL syntax error.
        mockMvc.perform(get(BASE + "/search?q=%2B%2D%22%28convocation")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isOk());
    }

    @Test
    void searchDoesNotLeakUniversityScopedArticles() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.leak@test.com", "Leak University");
        createArticle(university, new CreateNewsArticleRequest(
                "Restricted scholarship briefing", "s", "Internal scholarship briefing details",
                NewsCategory.ACADEMIC, NewsVisibility.UNIVERSITY, NewsStatus.PUBLISHED,
                null, null, null, null));

        String outsider = registerStudentAndGetToken("news.leak.outsider@test.com", "NL1001");

        mockMvc.perform(get(BASE + "/search?q=scholarship")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    // ── Engagement ────────────────────────────────────────────────────────────

    @Test
    void likeAndSaveToggleAndAppearInListings() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.engage@test.com", "Engage University");
        Long articleId = createPublishedArticle(university, "Engageable headline", "Body text here", null);
        String reader = registerStudentAndGetToken("news.engage.reader@test.com", "NE1001");
        String auth = "Bearer " + reader;

        mockMvc.perform(post(BASE + "/{id}/like", articleId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likesCount").value(1));
        mockMvc.perform(get(BASE + "/liked").header("Authorization", auth))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(articleId.intValue())));
        mockMvc.perform(post(BASE + "/{id}/like", articleId).header("Authorization", auth))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likesCount").value(0));

        mockMvc.perform(post(BASE + "/{id}/save", articleId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));
        mockMvc.perform(get(BASE + "/saved").header("Authorization", auth))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(articleId.intValue())));
        mockMvc.perform(post(BASE + "/{id}/save", articleId).header("Authorization", auth))
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    // ── Update and delete ─────────────────────────────────────────────────────

    @Test
    void authorCanUpdateButOthersCannot() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.upd@test.com", "Update University");
        Long articleId = createPublishedArticle(university, "Original headline", "Body text here", null);
        String other = registerEmployerAndGetToken("news.upd.other@test.com", "Other Ltd");

        String body = objectMapper.writeValueAsString(new UpdateNewsArticleRequest(
                "Revised headline", null, null, null, null, null, List.of("revised"), null, null));

        mockMvc.perform(put(BASE + "/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Revised headline"))
                .andExpect(jsonPath("$.data.tags[0]").value("revised"));

        mockMvc.perform(put(BASE + "/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void tagsCanBeReplacedWithAnOverlappingSet() throws Exception {
        // Regression guard: clear-then-re-add would flush the INSERT for a retained tag before the
        // DELETE of its old row and trip uq_news_tags.
        String university = registerUniversityAndGetToken("news.uni.tags@test.com", "Tag University");
        Long articleId = createPublishedArticle(university, "Retaggable headline", "Body text here",
                List.of("keep", "drop"));

        mockMvc.perform(put(BASE + "/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(new UpdateNewsArticleRequest(
                                null, null, null, null, null, null,
                                List.of("keep", "added"), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags", containsInAnyOrder("keep", "added")));

        // An empty list clears the set outright.
        mockMvc.perform(put(BASE + "/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(new UpdateNewsArticleRequest(
                                null, null, null, null, null, null, List.of(), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags", hasSize(0)));
    }

    @Test
    void deleteIsAuthorOrAdminOnlyAndSoftDeletes() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.del@test.com", "Delete University");
        Long articleId = createPublishedArticle(university, "Deletable headline", "Body text here", null);
        String other = registerEmployerAndGetToken("news.del.other@test.com", "Other Ltd");

        mockMvc.perform(delete(BASE + "/{id}", articleId).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/{id}", articleId).header("Authorization", "Bearer " + university))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + university))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanDeleteAnyArticle() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.adel@test.com", "AdminDel University");
        Long articleId = createPublishedArticle(university, "Admin removable", "Body text here", null);

        mockMvc.perform(delete(BASE + "/{id}", articleId)
                        .header("Authorization", "Bearer " + registerAdminAndGetToken("news.admin.del@test.com")))
                .andExpect(status().isOk());
    }

    // ── Media ─────────────────────────────────────────────────────────────────

    @Test
    void coverImageComesBackAsAPresignedUrlNotABareKey() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.cover@test.com", "Cover University");
        Long authorId = getUserId(university);
        String coverKey = "news/" + authorId + "/cover.jpg";

        Long articleId = createArticle(university, new CreateNewsArticleRequest(
                "With a cover", "s", "Body text here", null, null, NewsStatus.PUBLISHED,
                null, coverKey, null, null));

        mockMvc.perform(get(BASE + "/{id}", articleId).header("Authorization", "Bearer " + university))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImageUrl").value(startsWith("https://mock-storage.example.com")));
    }

    @Test
    void anotherUsersMediaKeyCannotBeAttached() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.steal@test.com", "Steal University");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(new CreateNewsArticleRequest(
                                "Borrowed cover", null, "Body", null, null, null, null,
                                "news/999999/someone-elses.jpg", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void blankTitleFailsValidation() throws Exception {
        String university = registerUniversityAndGetToken("news.uni.valid@test.com", "Valid University");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(new CreateNewsArticleRequest(
                                "  ", null, "Body", null, null, null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
}
