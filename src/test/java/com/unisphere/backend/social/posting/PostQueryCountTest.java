package com.unisphere.backend.social.posting;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards against N+1 regressions in the list endpoints.
 *
 * Rather than pinning an absolute query count — which drifts with unrelated changes and depends on
 * how much data other tests left behind — each case asserts that the count does not *scale* with
 * the number of rows rendered. That is the property that actually distinguishes batched loading
 * from N+1, and it holds regardless of the baseline.
 *
 * These tests fail loudly if someone reintroduces a per-row lookup: rendering 8 more posts used to
 * cost roughly 8 extra queries each for author, media, tags, like flag, save flag and comment
 * count.
 */
class PostQueryCountTest extends AbstractPostingIntegrationTest {

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
        // Spread posts across several authors so a shared persistence context can't dedupe the
        // author lookups and hide an N+1 that would still be there in production.
        String viewer = null;
        for (int i = 0; i < 5; i++) {
            String token = registerStudentAndGetToken("qc.feed." + i + "@test.com", "QCF100" + i);
            createTextPost(token, "Query count probe A " + i);
            createTextPost(token, "Query count probe B " + i);
            viewer = token;
        }
        String token = viewer;

        long small = countQueries(() ->
                mockMvc.perform(get("/api/v1/posts?page=0&size=2")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get("/api/v1/posts?page=0&size=10")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("feed queries grew from %d to %d when the page went from 2 to 10 posts — "
                        + "a per-post lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void comments_queryCountDoesNotScaleWithPageSize() throws Exception {
        String author = registerStudentAndGetToken("qc.comments@test.com", "QCC1001");
        Long postId = createTextPost(author, "Post carrying many comments");

        for (int i = 0; i < 5; i++) {
            String commenter = registerStudentAndGetToken("qc.commenter." + i + "@test.com", "QCM100" + i);
            createComment(commenter, postId, "Comment A " + i);
            createComment(commenter, postId, "Comment B " + i);
        }

        long small = countQueries(() ->
                mockMvc.perform(get("/api/v1/posts/{postId}/comments?page=0&size=2", postId)
                                .header("Authorization", "Bearer " + author))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get("/api/v1/posts/{postId}/comments?page=0&size=10", postId)
                                .header("Authorization", "Bearer " + author))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("comment queries grew from %d to %d when the page went from 2 to 10 comments — "
                        + "a per-comment lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }
}
