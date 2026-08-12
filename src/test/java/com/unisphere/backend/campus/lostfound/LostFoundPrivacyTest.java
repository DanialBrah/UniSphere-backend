package com.unisphere.backend.campus.lostfound;

import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The enforcement backstop for the privacy guard.
 *
 * <p>{@code LostFoundAccessService.locationViewFor} is an imperative invariant with no database rule
 * behind it: any endpoint that renders an item without routing through
 * {@code LostFoundService.toResponse}/{@code toSummaryResponse}/{@code toPin} silently leaks exact
 * coordinates, the pickup point and the full gallery, with no compile error to catch it.
 *
 * <p><b>Any new endpoint that renders a LostFoundItem must gain a test in the "per endpoint"
 * section below.</b>
 */
class LostFoundPrivacyTest extends AbstractLostFoundIntegrationTest {

    private static final BigDecimal EXACT_LAT = new BigDecimal("3.0678431");
    private static final BigDecimal EXACT_LNG = new BigDecimal("101.5006219");

    // What 2-decimal coarsening turns the constants above into. Compared as doubles, not
    // BigDecimals: jsonPath deserialises a JSON number to a Double, so new BigDecimal("101.50")
    // would fail against 101.5 on scale alone even though the values are equal.
    private static final double COARSE_LAT = 3.07;
    private static final double COARSE_LNG = 101.50;

    @Test
    void foundItem_nonOwnerSeesRoundedCoordinatesAndNullPickup() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter1@test.com");
        String stranger = registerStudentAndGetToken("priv-stranger1@test.com", "PRIV001");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data.incidentLatitude").value(COARSE_LAT))
                .andExpect(jsonPath("$.data.incidentLongitude").value(COARSE_LNG))
                .andExpect(jsonPath("$.data.pickupPlace").doesNotExist())
                .andExpect(jsonPath("$.data.pickupLatitude").doesNotExist())
                .andExpect(jsonPath("$.data.pickupLongitude").doesNotExist())
                .andExpect(jsonPath("$.data.pickupInstructions").doesNotExist())
                // The incident *label* stays — "where I found it" is the point of a found report.
                .andExpect(jsonPath("$.data.incidentPlace").value("Test incident place"));
    }

    @Test
    void foundItem_ownerSeesExactCoordinatesAndPickup() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter2@test.com");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coordinatesApproximate").value(false))
                .andExpect(jsonPath("$.data.incidentLatitude").value(EXACT_LAT))
                .andExpect(jsonPath("$.data.pickupPlace").value("Security Post A"))
                .andExpect(jsonPath("$.data.pickupInstructions").value("Test pickup instructions"));
    }

    @Test
    void foundItem_adminSeesExactCoordinatesAndPickup() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter3@test.com");
        String admin = registerAdminAndGetToken("priv-admin3@test.com");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coordinatesApproximate").value(false))
                .andExpect(jsonPath("$.data.incidentLatitude").value(EXACT_LAT))
                .andExpect(jsonPath("$.data.pickupPlace").value("Security Post A"));
    }

    @Test
    void foundItem_pendingClaimantStillSeesMaskedLocation() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter4@test.com");
        String claimant = registerStudentAndGetToken("priv-claimant4@test.com", "PRIV004");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");
        submitClaim(claimant, itemId, "That is my wallet, it has a blue library card inside.");

        // A pending claim is an assertion, not a verification — it must unlock nothing.
        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + claimant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data.pickupPlace").doesNotExist())
                .andExpect(jsonPath("$.data.viewerClaimStatus").value("PENDING"));
    }

    @Test
    void foundItem_approvedClaimantSeesExactPickup() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter5@test.com");
        String claimant = registerStudentAndGetToken("priv-claimant5@test.com", "PRIV005");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");
        submitAndApproveClaim(claimant, reporter, itemId);

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + claimant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coordinatesApproximate").value(false))
                .andExpect(jsonPath("$.data.incidentLatitude").value(EXACT_LAT))
                .andExpect(jsonPath("$.data.pickupPlace").value("Security Post A"))
                .andExpect(jsonPath("$.data.pickupInstructions").value("Test pickup instructions"));
    }

    @Test
    void foundItem_rejectedClaimantSeesMaskedLocation() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter6@test.com");
        String claimant = registerStudentAndGetToken("priv-claimant6@test.com", "PRIV006");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, "I am fairly sure that wallet belongs to me.");
        decideClaim(reporter, claimId, LostFoundClaimStatus.REJECTED);

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + claimant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data.pickupPlace").doesNotExist());
    }

    @Test
    void foundItem_identifyingDetailIsNullEvenForApprovedClaimant() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter7@test.com");
        String claimant = registerStudentAndGetToken("priv-claimant7@test.com", "PRIV007");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");
        submitAndApproveClaim(claimant, reporter, itemId);

        // The adjudication secret is owner-only at every claim status — there is no point in the
        // lifecycle at which revealing it helps.
        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + claimant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifyingDetail").doesNotExist());

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifyingDetail").value("The withheld secret detail"));
    }

    @Test
    void lostItem_everyViewerSeesExactCoordinates() throws Exception {
        String reporter = registerStudentAndGetToken("priv-reporter8@test.com", "PRIV008");
        String stranger = registerStudentAndGetToken("priv-stranger8@test.com", "PRIV008B");
        Long itemId = reportLost(reporter, "Lost power bank", EXACT_LAT, EXACT_LNG);

        // A LOST reporter wants maximum reach — nothing is withheld.
        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coordinatesApproximate").value(false))
                .andExpect(jsonPath("$.data.incidentLatitude").value(EXACT_LAT))
                .andExpect(jsonPath("$.data.incidentLongitude").value(EXACT_LNG));
    }

    @Test
    void maskedCoordinatesAreStableAcrossRepeatedReads() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-reporter9@test.com");
        String stranger = registerStudentAndGetToken("priv-stranger9@test.com", "PRIV009");
        Long itemId = reportFound(reporter, "Found wallet", EXACT_LAT, EXACT_LNG, "Security Post A");

        // Deterministic rounding, not jitter: a jittered value could be averaged back to the true
        // point over enough reads.
        String first = null;
        for (int i = 0; i < 5; i++) {
            MvcResult result = mockMvc.perform(get(BASE + "/items/{id}", itemId)
                            .header("Authorization", "Bearer " + stranger))
                    .andExpect(status().isOk())
                    .andReturn();
            String lat = readJson(result, "/data/incidentLatitude");
            if (first == null) first = lat;
            assertEquals(first, lat, "masked coordinate changed between reads");
        }
    }

    // ── One test per geo-exposing endpoint ───────────────────────────────────
    // Add to this block whenever a new endpoint renders an item.

    @Test
    void maskingHoldsOnListEndpoint() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-list@test.com");
        String stranger = registerStudentAndGetToken("priv-list-s@test.com", "PRIVL01");
        reportFound(reporter, "Found on the list endpoint", EXACT_LAT, EXACT_LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items").param("type", "FOUND")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data.content[0].pickupPlace").doesNotExist());
    }

    @Test
    void maskingHoldsOnSearchEndpoint() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-search@test.com");
        String stranger = registerStudentAndGetToken("priv-search-s@test.com", "PRIVS01");
        reportFound(reporter, "Distinctive searchable trombone", EXACT_LAT, EXACT_LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items/search").param("q", "trombone")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data.content[0].pickupPlace").doesNotExist());
    }

    @Test
    void maskingHoldsOnNearbyEndpoint() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-nearby@test.com");
        String stranger = registerStudentAndGetToken("priv-nearby-s@test.com", "PRIVN01");
        reportFound(reporter, "Found near the query point", EXACT_LAT, EXACT_LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", EXACT_LAT.toPlainString())
                        .param("lng", EXACT_LNG.toPlainString())
                        .param("radiusKm", "2")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].item.coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data.content[0].item.pickupPlace").doesNotExist());
    }

    @Test
    void maskingHoldsOnMapEndpoint() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-map@test.com");
        String stranger = registerStudentAndGetToken("priv-map-s@test.com", "PRIVM01");
        reportFound(reporter, "Found in the viewport", EXACT_LAT, EXACT_LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", "3.0").param("maxLat", "3.1")
                        .param("minLng", "101.4").param("maxLng", "101.6")
                        .param("type", "FOUND")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data[0].latitude").value(COARSE_LAT));
    }

    @Test
    void maskingHoldsOnMatchesEndpoint() throws Exception {
        String lostReporter = registerStudentAndGetToken("priv-match-l@test.com", "PRIVX01");
        String foundReporter = registerAlumniAndGetToken("priv-match-f@test.com");

        Long lostId = reportLost(lostReporter, "Lost black anker powerbank", EXACT_LAT, EXACT_LNG);
        reportFound(foundReporter, "Found black anker powerbank", EXACT_LAT, EXACT_LNG, "Security Post A");

        // The matches list is the reporter's own, but the FOUND items on it belong to someone else —
        // so they stay masked.
        mockMvc.perform(get(BASE + "/items/{id}/matches", lostId)
                        .header("Authorization", "Bearer " + lostReporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].item.coordinatesApproximate").value(true))
                .andExpect(jsonPath("$.data[0].item.pickupPlace").doesNotExist());
    }

    @Test
    void maskingHoldsOnMyItemsEndpoint() throws Exception {
        String reporter = registerAlumniAndGetToken("priv-mine@test.com");
        reportFound(reporter, "My own found item", EXACT_LAT, EXACT_LNG, "Security Post A");

        // Your own items are never masked — this pins that the endpoint still goes through the
        // guard rather than bypassing it and happening to look right.
        mockMvc.perform(get(BASE + "/items/me").header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].coordinatesApproximate").value(false))
                .andExpect(jsonPath("$.data.content[0].pickupPlace").value("Security Post A"));
    }
}
