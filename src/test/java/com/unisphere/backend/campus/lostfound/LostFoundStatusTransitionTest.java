package com.unisphere.backend.campus.lostfound;

import com.unisphere.backend.campus.lostfound.dto.request.LostFoundStatusUpdateRequest;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The item lifecycle, as enforced by {@code LostFoundService.changeStatus}. */
class LostFoundStatusTransitionTest extends AbstractLostFoundIntegrationTest {

    @Test
    void openToResolved_byReporter_succeedsAndSetsResolvedAt() throws Exception {
        String reporter = registerStudentAndGetToken("fsm1@test.com", "FSM001");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);

        patchStatus(reporter, itemId, LostFoundItemStatus.RESOLVED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedAt").exists());
    }

    @Test
    void openToCancelled_byReporter_succeeds() throws Exception {
        String reporter = registerStudentAndGetToken("fsm2@test.com", "FSM002");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);

        patchStatus(reporter, itemId, LostFoundItemStatus.CANCELLED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void openToClaimed_viaStatusEndpoint_returns409() throws Exception {
        String reporter = registerStudentAndGetToken("fsm3@test.com", "FSM003");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);

        // Claim approval is the only writer of CLAIMED — a hand-stamped one would have no claim row
        // behind it, and the privacy guard would disagree with the item's own status.
        patchStatus(reporter, itemId, LostFoundItemStatus.CLAIMED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_LOST_FOUND_STATUS_TRANSITION"));
    }

    @Test
    void openToExpired_viaStatusEndpoint_returns409() throws Exception {
        String reporter = registerStudentAndGetToken("fsm4@test.com", "FSM004");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);

        patchStatus(reporter, itemId, LostFoundItemStatus.EXPIRED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_LOST_FOUND_STATUS_TRANSITION"));
    }

    @Test
    void resolvedToOpen_returns409() throws Exception {
        String reporter = registerStudentAndGetToken("fsm5@test.com", "FSM005");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);
        changeItemStatus(reporter, itemId, LostFoundItemStatus.RESOLVED);

        patchStatus(reporter, itemId, LostFoundItemStatus.OPEN)
                .andExpect(status().isConflict());
    }

    @Test
    void cancelledToOpen_returns409() throws Exception {
        String reporter = registerStudentAndGetToken("fsm6@test.com", "FSM006");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);
        changeItemStatus(reporter, itemId, LostFoundItemStatus.CANCELLED);

        patchStatus(reporter, itemId, LostFoundItemStatus.OPEN)
                .andExpect(status().isConflict());
    }

    @Test
    void claimedToOpen_byReporter_leavesApprovedClaimAsHistory() throws Exception {
        String reporter = registerAlumniAndGetToken("fsm7@test.com");
        String claimant = registerStudentAndGetToken("fsm7c@test.com", "FSM007");
        Long itemId = reportFound(reporter, "Found bag", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");
        Long claimId = submitAndApproveClaim(claimant, reporter, itemId);

        // The handover fell through; the item goes back on the board but the claim stays as history.
        patchStatus(reporter, itemId, LostFoundItemStatus.OPEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(get(BASE + "/claims/{claimId}", claimId)
                        .header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void statusChange_byUnrelatedUser_returns403() throws Exception {
        String reporter = registerStudentAndGetToken("fsm8@test.com", "FSM008");
        String stranger = registerStudentAndGetToken("fsm8s@test.com", "FSM008B");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);

        patchStatus(stranger, itemId, LostFoundItemStatus.RESOLVED)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void statusChange_byAdmin_succeeds() throws Exception {
        String reporter = registerStudentAndGetToken("fsm9@test.com", "FSM009");
        String admin = registerAdminAndGetToken("fsm9a@test.com");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);

        patchStatus(admin, itemId, LostFoundItemStatus.CANCELLED)
                .andExpect(status().isOk());
    }

    @Test
    void sameStatus_returns409() throws Exception {
        String reporter = registerStudentAndGetToken("fsm10@test.com", "FSM010");
        Long itemId = reportLost(reporter, "Lost keys", CAMPUS_LAT, CAMPUS_LNG);

        patchStatus(reporter, itemId, LostFoundItemStatus.OPEN)
                .andExpect(status().isConflict());
    }

    @Test
    void approvingClaim_movesItemToClaimed() throws Exception {
        String reporter = registerAlumniAndGetToken("fsm11@test.com");
        String claimant = registerStudentAndGetToken("fsm11c@test.com", "FSM011");
        Long itemId = reportFound(reporter, "Found bag", CAMPUS_LAT, CAMPUS_LNG, "Security Post A");

        Long claimId = submitClaim(claimant, itemId, "That bag is mine, it has a red keyring on it.");
        decideClaim(reporter, claimId, LostFoundClaimStatus.APPROVED);

        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + reporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLAIMED"));
    }

    private org.springframework.test.web.servlet.ResultActions patchStatus(
            String token, Long itemId, LostFoundItemStatus status) throws Exception {
        return mockMvc.perform(patch(BASE + "/items/{itemId}/status", itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(
                        new LostFoundStatusUpdateRequest(status, "test note"))));
    }
}
