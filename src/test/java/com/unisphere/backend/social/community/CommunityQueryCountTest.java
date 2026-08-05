package com.unisphere.backend.social.community;

import com.unisphere.backend.social.community.enums.CommunityVisibility;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards against N+1 regressions in the community member/feed list endpoints. Asserts the query
 * count does not *scale* with the number of rows rendered, rather than pinning an absolute count —
 * see NewsQueryCountTest for the same rationale.
 */
class CommunityQueryCountTest extends AbstractCommunityIntegrationTest {

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
    void memberList_queryCountDoesNotScaleWithPageSize() throws Exception {
        String ownerToken = registerClubAndGetToken("nqc.members.owner@test.com", "QC Members Club", null);
        Long communityId = createCommunity(ownerToken, "Query Count Members Community", CommunityVisibility.PUBLIC);

        for (int i = 0; i < 10; i++) {
            String memberToken = registerStudentAndGetToken("nqc.members." + i + "@test.com", "NQCM100" + i);
            joinCommunity(memberToken, communityId);
        }

        long small = countQueries(() ->
                mockMvc.perform(get(BASE + "/{communityId}/members?page=0&size=2", communityId)
                                .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get(BASE + "/{communityId}/members?page=0&size=10", communityId)
                                .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("community member list queries grew from %d to %d when the page went from 2 to 10 — "
                        + "a per-member lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void discoverFeed_queryCountDoesNotScaleWithPageSize() throws Exception {
        String viewer = null;
        for (int i = 0; i < 8; i++) {
            String token = registerClubAndGetToken("nqc.discover." + i + "@test.com", "QC Discover Club " + i, null);
            createCommunity(token, "Discover Probe Community " + i, CommunityVisibility.PUBLIC);
            viewer = token;
        }
        String token = viewer;

        long small = countQueries(() ->
                mockMvc.perform(get(BASE + "?page=0&size=2").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get(BASE + "?page=0&size=8").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("community discovery feed queries grew from %d to %d when the page went from 2 to 8 — "
                        + "a per-community lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }
}
