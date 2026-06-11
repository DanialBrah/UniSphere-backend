package com.unisphere.backend.social.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import com.unisphere.backend.social.messaging.dto.request.CreateConversationRequest;
import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import com.unisphere.backend.social.messaging.enums.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class AbstractMessagingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    protected SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    protected RedisTemplate<String, Long> redisTemplate;

    @MockitoBean
    protected S3Client s3Client;

    @MockitoBean
    protected S3Presigner s3Presigner;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpMessagingMocks() {
        ListOperations<String, String> listOps = Mockito.mock(ListOperations.class);
        Mockito.lenient().when(stringRedisTemplate.opsForList()).thenReturn(listOps);
        Mockito.lenient().when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);
        Mockito.lenient().doNothing().when(listOps).trim(anyString(), anyLong(), anyLong());
        Mockito.lenient().when(listOps.range(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());
        Mockito.lenient().when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        Mockito.lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);
    }

    protected String registerStudentAndGetToken(String email, String matric) throws Exception {
        RegisterStudentRequest req = new RegisterStudentRequest(
                email, "Password123!", "Test User",
                matric, null, null, null,
                null, null, null, null, null
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    protected Long getUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asLong();
    }

    protected Long createDirectConversation(String token, Long participantId) throws Exception {
        CreateConversationRequest req = new CreateConversationRequest(
                ConversationType.DIRECT, List.of(participantId), null);
        MvcResult result = mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asLong();
    }

    protected Long createGroupConversation(String token, String name, Long... participantIds) throws Exception {
        List<Long> ids = Arrays.stream(participantIds).collect(Collectors.toList());
        CreateConversationRequest req = new CreateConversationRequest(ConversationType.GROUP, ids, name);
        MvcResult result = mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asLong();
    }

    protected Long sendMessage(String token, Long convId, String content) throws Exception {
        SendMessageRequest req = new SendMessageRequest(convId, content, MessageType.TEXT, null, null);
        MvcResult result = mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asLong();
    }
}
