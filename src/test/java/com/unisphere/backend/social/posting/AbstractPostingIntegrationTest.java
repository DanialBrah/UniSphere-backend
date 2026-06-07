package com.unisphere.backend.social.posting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for all social/posting integration tests.
 * Mocks Redis and B2 so tests only need a MySQL container.
 */
public abstract class AbstractPostingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    @MockitoBean
    protected RedisTemplate<String, Long> redisTemplate;

    @MockitoBean
    protected S3Client s3Client;

    @MockitoBean
    protected S3Presigner s3Presigner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpMocks() throws Exception {
        ValueOperations<String, Long> ops = Mockito.mock(ValueOperations.class);
        Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(ops);
        Mockito.lenient().when(ops.get(anyString())).thenReturn(null);
        Mockito.lenient().when(ops.increment(anyString())).thenReturn(1L);
        Mockito.lenient().when(ops.increment(anyString(), Mockito.anyLong())).thenReturn(1L);
        Mockito.lenient().when(ops.decrement(anyString())).thenReturn(0L);
        Mockito.lenient().when(ops.decrement(anyString(), Mockito.anyLong())).thenReturn(0L);
        Mockito.lenient().when(redisTemplate.keys(anyString())).thenReturn(Set.of());

        // Use typed matchers to resolve S3Client overload ambiguity
        Mockito.lenient()
                .doReturn(HeadBucketResponse.builder().build())
                .when(s3Client)
                .headBucket(any(Consumer.class));
        Mockito.lenient()
                .doReturn(DeleteObjectResponse.builder().build())
                .when(s3Client)
                .deleteObject(any(Consumer.class));

        PresignedPutObjectRequest fakePresigned = Mockito.mock(PresignedPutObjectRequest.class);
        Mockito.lenient()
                .when(fakePresigned.url())
                .thenReturn(URI.create("https://f004.backblazeb2.com/file/unisphere-posts/posts/1/test.jpg").toURL());
        Mockito.lenient()
                .when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(fakePresigned);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

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

    protected Long createTextPost(String token, String content) throws Exception {
        CreatePostRequest req = new CreatePostRequest(
                "Test Post", content, PostType.TEXT, PostVisibility.PUBLIC,
                null, null, null
        );
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asLong();
    }

    protected Long createComment(String token, Long postId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asLong();
    }
}
