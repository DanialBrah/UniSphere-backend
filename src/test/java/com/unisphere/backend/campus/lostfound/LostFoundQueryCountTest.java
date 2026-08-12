package com.unisphere.backend.campus.lostfound;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards against N+1 regressions in the lost &amp; found list endpoints.
 *
 * <p>Rather than pinning an absolute query count — which drifts with unrelated changes and depends
 * on how much data other tests left behind — each case asserts that the count does not <em>scale</em>
 * with the number of rows rendered. That is the property that actually distinguishes batched
 * loading from N+1, and it holds regardless of the baseline.
 */
class LostFoundQueryCountTest extends AbstractLostFoundIntegrationTest {

    /** Headroom for incidental variation (batch chunking, a larger IN list) without masking N+1. */
    private static final long ALLOWED_GROWTH = 5;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void feed_queryCountDoesNotScaleWithPageSize() throws Exception {
        // Spread items across several reporters so a shared persistence context can't dedupe the
        // reporter lookups and hide an N+1 that would still be there in production. Each item also
        // gets a pending claim, so all three PageContext maps are actually exercised.
        String viewer = registerStudentAndGetToken("lfqc.viewer@test.com", "QCV001");

        for (int i = 0; i < 5; i++) {
            String reporter = registerAlumniAndGetToken("lfqc.feed." + i + "@test.com");
            for (int j = 0; j < 2; j++) {
                Long itemId = reportFound(reporter, "Query count probe " + i + "-" + j,
                        CAMPUS_LAT, CAMPUS_LNG, "Security Post " + i);
                submitClaim(viewer, itemId, "Probe claim so the pending-count map is not empty.");
            }
        }
        String token = viewer;

        long small = countQueries(() ->
                mockMvc.perform(get(BASE + "/items?page=0&size=2").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get(BASE + "/items?page=0&size=10").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("lost & found feed queries grew from %d to %d when the page went from 2 to 10 items — "
                        + "the reporter, pending-claim-count or viewer-claim lookups are running per row", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void nearby_queryCountDoesNotScaleWithPageSize() throws Exception {
        String viewer = registerStudentAndGetToken("lfqc.near.viewer@test.com", "QCV002");

        for (int i = 0; i < 5; i++) {
            String reporter = registerAlumniAndGetToken("lfqc.near." + i + "@test.com");
            for (int j = 0; j < 2; j++) {
                // Nudge each item slightly so they are distinct points inside the radius.
                BigDecimal lat = CAMPUS_LAT.add(new BigDecimal("0.000" + (i + 1) + (j + 1)));
                reportFound(reporter, "Nearby probe " + i + "-" + j, lat, CAMPUS_LNG, "Security Post " + i);
            }
        }
        String token = viewer;

        long small = countQueries(() -> mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", CAMPUS_LAT.toPlainString())
                        .param("lng", CAMPUS_LNG.toPlainString())
                        .param("radiusKm", "10")
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));

        long large = countQueries(() -> mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", CAMPUS_LAT.toPlainString())
                        .param("lng", CAMPUS_LNG.toPlainString())
                        .param("radiusKm", "10")
                        .param("page", "0").param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));

        assertThat(large - small)
                .as("nearby queries grew from %d to %d when the page went from 2 to 10 items", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void map_queryCountDoesNotScaleWithPinCount() throws Exception {
        String viewer = registerStudentAndGetToken("lfqc.map.viewer@test.com", "QCV003");

        for (int i = 0; i < 5; i++) {
            String reporter = registerAlumniAndGetToken("lfqc.map." + i + "@test.com");
            for (int j = 0; j < 2; j++) {
                BigDecimal lat = CAMPUS_LAT.add(new BigDecimal("0.00" + (i + 1) + (j + 1)));
                reportFound(reporter, "Map probe " + i + "-" + j, lat, CAMPUS_LNG, "Security Post " + i);
            }
        }
        String token = viewer;

        // The approved-claim lookup behind the pin mask must be one query for the whole viewport,
        // not one per pin — a 500-pin viewport would otherwise issue 500 claim lookups.
        long narrow = countQueries(() -> mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", "3.0670").param("maxLat", "3.0700")
                        .param("minLng", "101.4").param("maxLng", "101.6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));

        long wide = countQueries(() -> mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", "3.0").param("maxLat", "3.2")
                        .param("minLng", "101.4").param("maxLng", "101.6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));

        assertThat(wide - narrow)
                .as("map queries grew from %d to %d when the viewport widened — "
                        + "the approved-claim lookup is running per pin", narrow, wide)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void myClaims_queryCountDoesNotScaleWithPageSize() throws Exception {
        String claimant = registerStudentAndGetToken("lfqc.claims@test.com", "QCV004");

        for (int i = 0; i < 10; i++) {
            String reporter = registerAlumniAndGetToken("lfqc.claims." + i + "@test.com");
            Long itemId = reportFound(reporter, "Claim probe " + i, CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
            submitClaim(claimant, itemId, "Probe claim number " + i + " with enough characters.");
        }

        long small = countQueries(() ->
                mockMvc.perform(get(BASE + "/claims/me?page=0&size=2")
                                .header("Authorization", "Bearer " + claimant))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get(BASE + "/claims/me?page=0&size=10")
                                .header("Authorization", "Bearer " + claimant))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("my-claims queries grew from %d to %d when the page went from 2 to 10 claims — "
                        + "the item-title or claimant lookups are running per row", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

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
}
