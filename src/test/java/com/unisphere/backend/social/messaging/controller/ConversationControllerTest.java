package com.unisphere.backend.social.messaging.controller;

import com.unisphere.backend.social.messaging.AbstractMessagingIntegrationTest;
import com.unisphere.backend.social.messaging.dto.request.AddMemberRequest;
import com.unisphere.backend.social.messaging.dto.request.CreateConversationRequest;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConversationControllerTest extends AbstractMessagingIntegrationTest {

    private static final String BASE = "/api/v1/conversations";

    // ── Create conversation ───────────────────────────────────────────────────

    @Test
    void createDirectConversation_validRequest_returns201() throws Exception {
        String tokenA = registerStudentAndGetToken("conv.direct.a@test.com", "MSG1001");
        String tokenB = registerStudentAndGetToken("conv.direct.b@test.com", "MSG1002");
        Long userBId = getUserId(tokenB);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenA)
                        .content(objectMapper.writeValueAsString(
                                new CreateConversationRequest(ConversationType.DIRECT, List.of(userBId), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.convType").value("DIRECT"))
                .andExpect(jsonPath("$.data.members", hasSize(2)));
    }

    @Test
    void createDirectConversation_calledTwice_returnsSameConversation() throws Exception {
        String tokenA = registerStudentAndGetToken("conv.idem.a@test.com", "MSG1003");
        String tokenB = registerStudentAndGetToken("conv.idem.b@test.com", "MSG1004");
        Long userBId = getUserId(tokenB);

        Long firstId = createDirectConversation(tokenA, userBId);
        Long secondId = createDirectConversation(tokenA, userBId);

        org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId, "Expected same conversation ID on duplicate direct conv creation");
    }

    @Test
    void createGroupConversation_validRequest_returns201() throws Exception {
        String tokenA = registerStudentAndGetToken("conv.grp.a@test.com", "MSG1005");
        String tokenB = registerStudentAndGetToken("conv.grp.b@test.com", "MSG1006");
        String tokenC = registerStudentAndGetToken("conv.grp.c@test.com", "MSG1007");
        Long userBId = getUserId(tokenB);
        Long userCId = getUserId(tokenC);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenA)
                        .content(objectMapper.writeValueAsString(
                                new CreateConversationRequest(ConversationType.GROUP, List.of(userBId, userCId), "Study Group"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.convType").value("GROUP"))
                .andExpect(jsonPath("$.data.name").value("Study Group"))
                .andExpect(jsonPath("$.data.members", hasSize(3)));
    }

    @Test
    void createConversation_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateConversationRequest(ConversationType.DIRECT, List.of(99L), null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDirectConversation_emptyParticipants_returns400() throws Exception {
        String token = registerStudentAndGetToken("conv.badreq@test.com", "MSG1008");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateConversationRequest(ConversationType.DIRECT, List.of(), null))))
                .andExpect(status().isBadRequest());
    }

    // ── Get inbox ─────────────────────────────────────────────────────────────

    @Test
    void getInbox_withConversations_returns200() throws Exception {
        String tokenA = registerStudentAndGetToken("inbox.a@test.com", "MSG1009");
        String tokenB = registerStudentAndGetToken("inbox.b@test.com", "MSG1010");
        Long userBId = getUserId(tokenB);
        createDirectConversation(tokenA, userBId);

        mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getInbox_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    // ── Get single conversation ───────────────────────────────────────────────

    @Test
    void getConversation_asMember_returns200() throws Exception {
        String tokenA = registerStudentAndGetToken("getconv.a@test.com", "MSG1011");
        String tokenB = registerStudentAndGetToken("getconv.b@test.com", "MSG1012");
        Long userBId = getUserId(tokenB);
        Long convId = createDirectConversation(tokenA, userBId);

        mockMvc.perform(get(BASE + "/{convId}", convId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(convId))
                .andExpect(jsonPath("$.data.members", hasSize(2)));
    }

    @Test
    void getConversation_asNonMember_returns403() throws Exception {
        String tokenA = registerStudentAndGetToken("getconv.owner@test.com", "MSG1013");
        String tokenB = registerStudentAndGetToken("getconv.member@test.com", "MSG1014");
        String tokenC = registerStudentAndGetToken("getconv.outsider@test.com", "MSG1015");
        Long userBId = getUserId(tokenB);
        Long convId = createDirectConversation(tokenA, userBId);

        mockMvc.perform(get(BASE + "/{convId}", convId)
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_CONVERSATION_MEMBER"));
    }

    @Test
    void getConversation_nonExistingId_returns404() throws Exception {
        String token = registerStudentAndGetToken("getconv.notfound@test.com", "MSG1016");

        mockMvc.perform(get(BASE + "/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── Add member ────────────────────────────────────────────────────────────

    @Test
    void addMember_asAdmin_returns201() throws Exception {
        String tokenAdmin = registerStudentAndGetToken("addmem.admin@test.com", "MSG1017");
        String tokenB     = registerStudentAndGetToken("addmem.b@test.com", "MSG1018");
        String tokenC     = registerStudentAndGetToken("addmem.c@test.com", "MSG1019");
        Long userBId = getUserId(tokenB);
        Long userCId = getUserId(tokenC);
        Long convId = createGroupConversation(tokenAdmin, "Admin Group", userBId);

        mockMvc.perform(post(BASE + "/{convId}/members", convId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest(userCId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(userCId));
    }

    @Test
    void addMember_asNonAdmin_returns403() throws Exception {
        String tokenAdmin = registerStudentAndGetToken("addmem.owner2@test.com", "MSG1020");
        String tokenMember = registerStudentAndGetToken("addmem.member2@test.com", "MSG1021");
        String tokenNew   = registerStudentAndGetToken("addmem.new2@test.com", "MSG1022");
        Long memberId = getUserId(tokenMember);
        Long newUserId = getUserId(tokenNew);
        Long convId = createGroupConversation(tokenAdmin, "Members Group", memberId);

        mockMvc.perform(post(BASE + "/{convId}/members", convId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenMember)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest(newUserId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── Remove member ─────────────────────────────────────────────────────────

    @Test
    void removeMember_asAdmin_returns200() throws Exception {
        String tokenAdmin  = registerStudentAndGetToken("remmem.admin@test.com", "MSG1023");
        String tokenMember = registerStudentAndGetToken("remmem.member@test.com", "MSG1024");
        String tokenExtra  = registerStudentAndGetToken("remmem.extra@test.com", "MSG1025");
        Long memberId = getUserId(tokenMember);
        Long extraId  = getUserId(tokenExtra);
        Long convId = createGroupConversation(tokenAdmin, "Remove Group", memberId, extraId);

        mockMvc.perform(delete(BASE + "/{convId}/members/{userId}", convId, memberId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void removeMember_self_returns200() throws Exception {
        String tokenAdmin  = registerStudentAndGetToken("leave.admin@test.com", "MSG1026");
        String tokenMember = registerStudentAndGetToken("leave.member@test.com", "MSG1027");
        Long memberId = getUserId(tokenMember);
        Long convId = createGroupConversation(tokenAdmin, "Leave Group", memberId);

        mockMvc.perform(delete(BASE + "/{convId}/members/{userId}", convId, memberId)
                        .header("Authorization", "Bearer " + tokenMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
