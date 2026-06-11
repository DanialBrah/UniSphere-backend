package com.unisphere.backend.social.messaging.controller;

import com.unisphere.backend.social.messaging.AbstractMessagingIntegrationTest;
import com.unisphere.backend.social.messaging.dto.request.MarkReadRequest;
import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import com.unisphere.backend.social.messaging.enums.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessageControllerTest extends AbstractMessagingIntegrationTest {

    private static final String BASE = "/api/v1/messages";

    // ── Send message ──────────────────────────────────────────────────────────

    @Test
    void sendMessage_validRequest_returns201() throws Exception {
        String tokenA = registerStudentAndGetToken("msg.send.a@test.com", "MSG2001");
        String tokenB = registerStudentAndGetToken("msg.send.b@test.com", "MSG2002");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenA)
                        .content(objectMapper.writeValueAsString(
                                new SendMessageRequest(convId, "Hello!", MessageType.TEXT, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.content").value("Hello!"))
                .andExpect(jsonPath("$.data.msgType").value("TEXT"))
                .andExpect(jsonPath("$.data.conversationId").value(convId))
                .andExpect(jsonPath("$.data.replyToId").value(nullValue()));
    }

    @Test
    void sendMessage_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendMessageRequest(1L, "Hello!", MessageType.TEXT, null, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_asNonMember_returns403() throws Exception {
        String tokenA      = registerStudentAndGetToken("msg.nonmem.a@test.com", "MSG2003");
        String tokenB      = registerStudentAndGetToken("msg.nonmem.b@test.com", "MSG2004");
        String tokenOther  = registerStudentAndGetToken("msg.nonmem.c@test.com", "MSG2005");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenOther)
                        .content(objectMapper.writeValueAsString(
                                new SendMessageRequest(convId, "Sneaky!", MessageType.TEXT, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_CONVERSATION_MEMBER"));
    }

    @Test
    void sendMessage_withNullReplyToId_returns201() throws Exception {
        String tokenA = registerStudentAndGetToken("msg.reply.null.a@test.com", "MSG2006");
        String tokenB = registerStudentAndGetToken("msg.reply.null.b@test.com", "MSG2007");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenA)
                        .content(objectMapper.writeValueAsString(
                                new SendMessageRequest(convId, "No reply", MessageType.TEXT, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.replyToId").value(nullValue()));
    }

    @Test
    void sendMessage_withZeroReplyToId_returns400() throws Exception {
        String tokenA = registerStudentAndGetToken("msg.reply.zero.a@test.com", "MSG2008");
        String tokenB = registerStudentAndGetToken("msg.reply.zero.b@test.com", "MSG2009");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));

        // replyToId=0 was causing a 500 FK constraint violation before the fix.
        // @Positive on the DTO now rejects it with a 400 validation error instead.
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenA)
                        .content("{\"conversationId\":" + convId + ",\"content\":\"Hey\",\"msgType\":\"TEXT\",\"replyToId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void sendMessage_withValidReplyToId_returns201() throws Exception {
        String tokenA = registerStudentAndGetToken("msg.reply.valid.a@test.com", "MSG2010");
        String tokenB = registerStudentAndGetToken("msg.reply.valid.b@test.com", "MSG2011");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));
        Long originalMsgId = sendMessage(tokenA, convId, "Original message");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenB)
                        .content(objectMapper.writeValueAsString(
                                new SendMessageRequest(convId, "Replying!", MessageType.TEXT, null, originalMsgId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.replyToId").value(originalMsgId));
    }

    // ── Get history ───────────────────────────────────────────────────────────

    @Test
    void getHistory_asMember_returns200WithMessages() throws Exception {
        String tokenA = registerStudentAndGetToken("hist.a@test.com", "MSG2012");
        String tokenB = registerStudentAndGetToken("hist.b@test.com", "MSG2013");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));
        sendMessage(tokenA, convId, "Message one");
        sendMessage(tokenB, convId, "Message two");

        mockMvc.perform(get(BASE)
                        .param("conversationId", convId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getHistory_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE).param("conversationId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHistory_asNonMember_returns403() throws Exception {
        String tokenA     = registerStudentAndGetToken("hist.owner@test.com", "MSG2014");
        String tokenB     = registerStudentAndGetToken("hist.peer@test.com", "MSG2015");
        String tokenOther = registerStudentAndGetToken("hist.outsider@test.com", "MSG2016");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));
        sendMessage(tokenA, convId, "Private message");

        mockMvc.perform(get(BASE)
                        .param("conversationId", convId.toString())
                        .header("Authorization", "Bearer " + tokenOther))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_CONVERSATION_MEMBER"));
    }

    // ── Delete message ────────────────────────────────────────────────────────

    @Test
    void deleteMessage_ownMessage_softDeletesAndReturns200() throws Exception {
        String tokenA = registerStudentAndGetToken("del.own.a@test.com", "MSG2017");
        String tokenB = registerStudentAndGetToken("del.own.b@test.com", "MSG2018");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));
        Long msgId = sendMessage(tokenA, convId, "To be deleted");

        mockMvc.perform(delete(BASE + "/{messageId}", msgId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Soft-deleted message must not appear in history
        mockMvc.perform(get(BASE)
                        .param("conversationId", convId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void deleteMessage_asNonOwner_returns403() throws Exception {
        String tokenA = registerStudentAndGetToken("del.other.a@test.com", "MSG2019");
        String tokenB = registerStudentAndGetToken("del.other.b@test.com", "MSG2020");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));
        Long msgId = sendMessage(tokenA, convId, "Owner's message");

        mockMvc.perform(delete(BASE + "/{messageId}", msgId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteMessage_nonExistingId_returns404() throws Exception {
        String token = registerStudentAndGetToken("del.notfound@test.com", "MSG2021");

        mockMvc.perform(delete(BASE + "/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MESSAGE_NOT_FOUND"));
    }

    // ── Mark read ─────────────────────────────────────────────────────────────

    @Test
    void markRead_validRequest_returns200() throws Exception {
        String tokenA = registerStudentAndGetToken("read.a@test.com", "MSG2022");
        String tokenB = registerStudentAndGetToken("read.b@test.com", "MSG2023");
        Long convId = createDirectConversation(tokenA, getUserId(tokenB));
        Long msgId = sendMessage(tokenA, convId, "Read me");

        mockMvc.perform(post(BASE + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenB)
                        .content(objectMapper.writeValueAsString(new MarkReadRequest(convId, msgId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value(convId))
                .andExpect(jsonPath("$.data.lastReadMessageId").value(msgId));
    }
}
