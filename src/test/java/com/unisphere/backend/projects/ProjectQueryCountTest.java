package com.unisphere.backend.projects;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards against N+1 regressions in the project browse feed and applicant roster endpoints.
 * Asserts the query count does not *scale* with the number of rows rendered, rather than pinning
 * an absolute count — see CommunityQueryCountTest for the same rationale.
 */
class ProjectQueryCountTest extends AbstractProjectIntegrationTest {

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
        String viewer = null;
        for (int i = 0; i < 10; i++) {
            String token = registerStudentAndGetToken("nqc.pfeed." + i + "@test.com", "NQCF100" + i);
            createProject(token, "Query Count Feed Project " + i);
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
                .as("project feed queries grew from %d to %d when the page went from 2 to 10 — "
                        + "a per-project lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void applicantRoster_queryCountDoesNotScaleWithPageSize() throws Exception {
        String ownerToken = registerStudentAndGetToken("nqc.roster.owner@test.com", "NQCR1000");
        Long projectId = createProject(ownerToken, "Query Count Roster Project");
        Long roleId = addRole(ownerToken, projectId, "Contributor", 20);
        setRecruiting(ownerToken, projectId, true);

        for (int i = 0; i < 10; i++) {
            String applicantToken = registerAlumniAndGetToken("nqc.roster.applicant." + i + "@test.com");
            apply(applicantToken, projectId, roleId, "Applicant " + i);
        }

        long small = countQueries(() ->
                mockMvc.perform(get(BASE + "/{projectId}/applications?page=0&size=2", projectId)
                                .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get(BASE + "/{projectId}/applications?page=0&size=10", projectId)
                                .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("project applicant roster queries grew from %d to %d when the page went from 2 to 10 — "
                        + "a per-applicant lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }
}
