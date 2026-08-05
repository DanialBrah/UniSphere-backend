package com.unisphere.backend.campus.news;

import com.unisphere.backend.campus.news.dto.request.CreateNewsArticleRequest;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards against N+1 regressions in the news list endpoints.
 *
 * Rather than pinning an absolute query count — which drifts with unrelated changes and depends on
 * how much data other tests left behind — each case asserts that the count does not *scale* with
 * the number of rows rendered. That is the property that actually distinguishes batched loading
 * from N+1, and it holds regardless of the baseline.
 */
class NewsQueryCountTest extends AbstractNewsIntegrationTest {

    /** Headroom for incidental variation (batch chunking, a larger IN list) without masking N+1. */
    private static final long ALLOWED_GROWTH = 5;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private long countQueries(Action action) throws Exception {
        Statistics stats = statistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    @Test
    void feed_queryCountDoesNotScaleWithPageSize() throws Exception {
        // Spread articles across several authors so a shared persistence context can't dedupe the
        // author lookups and hide an N+1 that would still be there in production. Tags are attached
        // deliberately — without them the @BatchSize collection never loads and the test would pass
        // while the real feed N+1s.
        String viewer = null;
        for (int i = 0; i < 5; i++) {
            String token = registerUniversityAndGetToken("nqc.feed." + i + "@test.com", "QC University " + i);
            for (int j = 0; j < 2; j++) {
                createArticle(token, new CreateNewsArticleRequest(
                        "Query count probe " + i + "-" + j, "Summary", "Body text here",
                        NewsCategory.GENERAL, NewsVisibility.PUBLIC, NewsStatus.PUBLISHED,
                        null, null, List.of("probe" + i, "shared"), null));
            }
            viewer = token;
        }
        String token = viewer;

        long small = countQueries(() ->
                mockMvc.perform(get(BASE + "?page=0&size=2").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get(BASE + "?page=0&size=10").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("news feed queries grew from %d to %d when the page went from 2 to 10 articles — "
                        + "a per-article lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void comments_queryCountDoesNotScaleWithPageSize() throws Exception {
        String author = registerUniversityAndGetToken("nqc.comments@test.com", "QC Comment University");
        Long articleId = createPublishedArticle(author, "Article carrying many comments",
                "Body text here", List.of("busy"));

        for (int i = 0; i < 5; i++) {
            String commenter = registerStudentAndGetToken("nqc.commenter." + i + "@test.com", "NQC100" + i);
            createNewsComment(commenter, articleId, "Comment A " + i);
            createNewsComment(commenter, articleId, "Comment B " + i);
        }

        long small = countQueries(() ->
                mockMvc.perform(get(BASE + "/{articleId}/comments?page=0&size=2", articleId)
                                .header("Authorization", "Bearer " + author))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get(BASE + "/{articleId}/comments?page=0&size=10", articleId)
                                .header("Authorization", "Bearer " + author))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("news comment queries grew from %d to %d when the page went from 2 to 10 — "
                        + "a per-comment lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }
}
