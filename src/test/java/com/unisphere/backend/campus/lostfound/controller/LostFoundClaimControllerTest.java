package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.campus.lostfound.AbstractLostFoundIntegrationTest;
import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundClaimRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundClaimDecisionRequest;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LostFoundClaimControllerTest extends AbstractLostFoundIntegrationTest {

    private static final String GOOD_PROOF = "It is mine — brown leather with a blue library card inside.";

    @Test
    void submitClaim_onOthersItem_returns201AndReporterReceivesNotification() throws Exception {
        String reporter = registerAlumniAndGetToken("cl1r@test.com");
        String claimant = registerStudentAndGetToken("cl1c@test.com", "CL001");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");

        mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + claimant)
                        .content(objectMapper.writeValueAsString(
                                new CreateLostFoundClaimRequest(GOOD_PROOF, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.itemTitle").value("Found wallet"));

        // Asserted through the notification API rather than by verifying the mock: the push is an
        // AFTER_COMMIT / REQUIRES_NEW listener, so the persisted row is the reliable signal.
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].notifType").value("LOST_FOUND_CLAIM"));
    }

    @Test
    void submitClaim_onOwnItem_returns400() throws Exception {
        String reporter = registerAlumniAndGetToken("cl2r@test.com");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");

        mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + reporter)
                        .content(objectMapper.writeValueAsString(
                                new CreateLostFoundClaimRequest(GOOD_PROOF, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void submitClaim_secondPendingBySameClaimant_returns400() throws Exception {
        String reporter = registerAlumniAndGetToken("cl3r@test.com");
        String claimant = registerStudentAndGetToken("cl3c@test.com", "CL003");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        submitClaim(claimant, itemId, GOOD_PROOF);

        mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + claimant)
                        .content(objectMapper.writeValueAsString(
                                new CreateLostFoundClaimRequest(GOOD_PROOF, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitClaim_afterRejection_isAllowed() throws Exception {
        String reporter = registerAlumniAndGetToken("cl4r@test.com");
        String claimant = registerStudentAndGetToken("cl4c@test.com", "CL004");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);
        decideClaim(reporter, claimId, LostFoundClaimStatus.REJECTED);

        // Proves the deliberate absence of a (item_id, claimant_id) unique constraint: a rejected
        // claimant may come back with better proof.
        mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + claimant)
                        .content(objectMapper.writeValueAsString(new CreateLostFoundClaimRequest(
                                "Second attempt with better proof: it has my name embossed inside.", null))))
                .andExpect(status().isCreated());
    }

    @Test
    void submitClaim_onClaimedItem_returns400() throws Exception {
        String reporter = registerAlumniAndGetToken("cl5r@test.com");
        String first = registerStudentAndGetToken("cl5a@test.com", "CL005A");
        String second = registerStudentAndGetToken("cl5b@test.com", "CL005B");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        submitAndApproveClaim(first, reporter, itemId);

        mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + second)
                        .content(objectMapper.writeValueAsString(
                                new CreateLostFoundClaimRequest(GOOD_PROOF, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitClaim_proofTextTooShort_returns400() throws Exception {
        String reporter = registerAlumniAndGetToken("cl6r@test.com");
        String claimant = registerStudentAndGetToken("cl6c@test.com", "CL006");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");

        mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + claimant)
                        .content(objectMapper.writeValueAsString(
                                new CreateLostFoundClaimRequest("its mine", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void submitClaim_proofImageKeyOwnedByAnotherUser_returns403() throws Exception {
        String reporter = registerAlumniAndGetToken("cl7r@test.com");
        String claimant = registerStudentAndGetToken("cl7c@test.com", "CL007");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long reporterId = getUserId(reporter);

        mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + claimant)
                        .content(objectMapper.writeValueAsString(new CreateLostFoundClaimRequest(
                                GOOD_PROOF, "lost-found/" + reporterId + "/someone-elses.jpg"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void listClaims_byReporter_returnsAll() throws Exception {
        String reporter = registerAlumniAndGetToken("cl8r@test.com");
        String a = registerStudentAndGetToken("cl8a@test.com", "CL008A");
        String b = registerStudentAndGetToken("cl8b@test.com", "CL008B");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        submitClaim(a, itemId, GOOD_PROOF);
        submitClaim(b, itemId, "I lost a brown wallet in that exact spot last Tuesday afternoon.");

        mockMvc.perform(get(BASE + "/items/{itemId}/claims", itemId)
                        .header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listClaims_byClaimant_returnsOnlyOwn() throws Exception {
        String reporter = registerAlumniAndGetToken("cl9r@test.com");
        String a = registerStudentAndGetToken("cl9a@test.com", "CL009A");
        String b = registerStudentAndGetToken("cl9b@test.com", "CL009B");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        submitClaim(a, itemId, GOOD_PROOF);
        submitClaim(b, itemId, "I lost a brown wallet in that exact spot last Tuesday afternoon.");

        // A public list would expose every claimant's proof text to every other claimant.
        mockMvc.perform(get(BASE + "/items/{itemId}/claims", itemId)
                        .header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listClaims_byUnrelatedUser_returns403() throws Exception {
        String reporter = registerAlumniAndGetToken("cl10r@test.com");
        String claimant = registerStudentAndGetToken("cl10c@test.com", "CL010C");
        String stranger = registerStudentAndGetToken("cl10s@test.com", "CL010S");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        submitClaim(claimant, itemId, GOOD_PROOF);

        mockMvc.perform(get(BASE + "/items/{itemId}/claims", itemId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveClaim_flipsItemToClaimedAndAutoRejectsSiblings() throws Exception {
        String reporter = registerAlumniAndGetToken("cl11r@test.com");
        String winner = registerStudentAndGetToken("cl11w@test.com", "CL011W");
        String loserA = registerStudentAndGetToken("cl11a@test.com", "CL011A");
        String loserB = registerStudentAndGetToken("cl11b@test.com", "CL011B");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");

        Long winning = submitClaim(winner, itemId, GOOD_PROOF);
        submitClaim(loserA, itemId, "I am fairly sure that wallet is the one I lost near the gate.");
        submitClaim(loserB, itemId, "Lost a brown wallet recently, could well be that exact one.");

        decideClaim(reporter, winning, LostFoundClaimStatus.APPROVED);

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLAIMED"))
                // All three claims are now decided, so nothing is left pending.
                .andExpect(jsonPath("$.data.pendingClaimCount").value(0));

        mockMvc.perform(get(BASE + "/claims/me").header("Authorization", "Bearer " + loserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.content[0].decisionNote").value("Another claim was approved"));
    }

    @Test
    void approveClaim_byClaimant_returns403() throws Exception {
        String reporter = registerAlumniAndGetToken("cl12r@test.com");
        String claimant = registerStudentAndGetToken("cl12c@test.com", "CL012");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);

        decide(claimant, claimId, LostFoundClaimStatus.APPROVED)
                .andExpect(status().isForbidden());
    }

    @Test
    void approveClaim_alreadyDecided_returns409() throws Exception {
        String reporter = registerAlumniAndGetToken("cl13r@test.com");
        String claimant = registerStudentAndGetToken("cl13c@test.com", "CL013");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);
        decideClaim(reporter, claimId, LostFoundClaimStatus.REJECTED);

        decide(reporter, claimId, LostFoundClaimStatus.APPROVED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_LOST_FOUND_CLAIM_TRANSITION"));
    }

    @Test
    void rejectClaim_byAdmin_succeeds() throws Exception {
        String reporter = registerAlumniAndGetToken("cl14r@test.com");
        String claimant = registerStudentAndGetToken("cl14c@test.com", "CL014");
        String admin = registerAdminAndGetToken("cl14a@test.com");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);

        decide(admin, claimId, LostFoundClaimStatus.REJECTED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void cancelClaim_byClaimant_succeeds() throws Exception {
        String reporter = registerAlumniAndGetToken("cl15r@test.com");
        String claimant = registerStudentAndGetToken("cl15c@test.com", "CL015");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);

        decide(claimant, claimId, LostFoundClaimStatus.CANCELLED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                // Withdrawing is not a review, so nobody is recorded as the reviewer.
                .andExpect(jsonPath("$.data.reviewedBy").doesNotExist());
    }

    @Test
    void cancelClaim_byReporter_returns403() throws Exception {
        String reporter = registerAlumniAndGetToken("cl16r@test.com");
        String claimant = registerStudentAndGetToken("cl16c@test.com", "CL016");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);

        decide(reporter, claimId, LostFoundClaimStatus.CANCELLED)
                .andExpect(status().isForbidden());
    }

    @Test
    void decideClaim_toPending_returns409() throws Exception {
        String reporter = registerAlumniAndGetToken("cl17r@test.com");
        String claimant = registerStudentAndGetToken("cl17c@test.com", "CL017");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);

        decide(reporter, claimId, LostFoundClaimStatus.PENDING)
                .andExpect(status().isConflict());
    }

    @Test
    void myClaims_returnsClaimsAcrossItems() throws Exception {
        String reporterA = registerAlumniAndGetToken("cl18a@test.com");
        String reporterB = registerAlumniAndGetToken("cl18b@test.com");
        String claimant = registerStudentAndGetToken("cl18c@test.com", "CL018");
        Long itemA = reportFound(reporterA, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long itemB = reportFound(reporterB, "Found headphones", CAMPUS_LAT, CAMPUS_LNG, "Security Post B");
        submitClaim(claimant, itemA, GOOD_PROOF);
        submitClaim(claimant, itemB, "The headphones are mine, they have a red fox sticker on the case.");

        mockMvc.perform(get(BASE + "/claims/me").header("Authorization", "Bearer " + claimant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].itemTitle").exists());
    }

    @Test
    void getClaim_byUnrelatedUser_returns403() throws Exception {
        String reporter = registerAlumniAndGetToken("cl19r@test.com");
        String claimant = registerStudentAndGetToken("cl19c@test.com", "CL019C");
        String stranger = registerStudentAndGetToken("cl19s@test.com", "CL019S");
        Long itemId = reportFound(reporter, "Found wallet", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitClaim(claimant, itemId, GOOD_PROOF);

        mockMvc.perform(get(BASE + "/claims/{claimId}", claimId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions decide(
            String token, Long claimId, LostFoundClaimStatus status) throws Exception {
        return mockMvc.perform(patch(BASE + "/claims/{claimId}", claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(
                        new LostFoundClaimDecisionRequest(status, "Decided by test"))));
    }
}
