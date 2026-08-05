package com.unisphere.backend.social.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.identity.dto.LoginRequest;
import com.unisphere.backend.identity.dto.RegisterAlumniRequest;
import com.unisphere.backend.identity.dto.RegisterClubRequest;
import com.unisphere.backend.identity.dto.RegisterEmployerRequest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import com.unisphere.backend.identity.dto.RegisterUniversityRequest;
import com.unisphere.backend.identity.entity.Admin;
import com.unisphere.backend.identity.entity.UserStatus;
import com.unisphere.backend.identity.repository.AdminRepository;
import com.unisphere.backend.social.community.dto.request.CreateCommunityRequest;
import com.unisphere.backend.social.community.enums.CommunityVisibility;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for all social/community integration tests. Mocks the same infra AbstractNewsIntegrationTest
 * does (STOMP push, Redis counters, S3 presigning) so tests only need the shared MySQL container —
 * community banner keys and community post media both resolve through the same MediaUrlResolver.
 */
public abstract class AbstractCommunityIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123!";
    protected static final String BASE = "/api/v1/communities";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AdminRepository adminRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @MockitoBean
    protected SimpMessagingTemplate messagingTemplate;

    @SuppressWarnings("unchecked")
    @MockitoBean
    protected RedisTemplate<String, Long> redisTemplate;

    @MockitoBean
    protected S3Client s3Client;

    @MockitoBean
    protected S3Presigner s3Presigner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpCommunityMocks() throws Exception {
        ValueOperations<String, Long> ops = Mockito.mock(ValueOperations.class);
        Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(ops);
        Mockito.lenient().when(ops.get(anyString())).thenReturn(null);
        Mockito.lenient().when(ops.increment(anyString())).thenReturn(1L);
        Mockito.lenient().when(ops.increment(anyString(), Mockito.anyLong())).thenReturn(1L);
        Mockito.lenient().when(ops.decrement(anyString())).thenReturn(0L);
        Mockito.lenient().when(ops.decrement(anyString(), Mockito.anyLong())).thenReturn(0L);
        Mockito.lenient().when(redisTemplate.keys(anyString())).thenReturn(Set.of());

        Mockito.lenient().doReturn(HeadBucketResponse.builder().build())
                .when(s3Client).headBucket(any(Consumer.class));
        Mockito.lenient().doReturn(DeleteObjectResponse.builder().build())
                .when(s3Client).deleteObject(any(Consumer.class));

        PresignedPutObjectRequest fakePut = Mockito.mock(PresignedPutObjectRequest.class);
        Mockito.lenient().when(fakePut.url())
                .thenReturn(URI.create("https://mock-storage.example.com/posts/community/test.jpg").toURL());
        Mockito.lenient().when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(fakePut);

        PresignedGetObjectRequest fakeGet = Mockito.mock(PresignedGetObjectRequest.class);
        Mockito.lenient().when(fakeGet.url())
                .thenReturn(URI.create("https://mock-storage.example.com/posts/community/test.jpg?get").toURL());
        Mockito.lenient().when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(fakeGet);
    }

    // ── Registration helpers ──────────────────────────────────────────────────

    protected String registerStudentAndGetToken(String email, String matric) throws Exception {
        return registerAndGetToken("/api/v1/auth/register/student",
                new RegisterStudentRequest(email, PASSWORD, "Test Student", matric,
                        null, null, null, null, null, null, null, null));
    }

    protected String registerAlumniAndGetToken(String email) throws Exception {
        return registerAndGetToken("/api/v1/auth/register/alumni",
                new RegisterAlumniRequest(email, PASSWORD, "Test Alumni", null, null,
                        null, null, null, null, null, null));
    }

    protected String registerUniversityAndGetToken(String email, String name) throws Exception {
        return registerAndGetToken("/api/v1/auth/register/university",
                new RegisterUniversityRequest(email, PASSWORD, name, null, null, null, null, null, null));
    }

    /** RegisterClubRequest requires a real universityId — a throwaway one is provisioned when null is passed. */
    protected String registerClubAndGetToken(String email, String name, Long universityId) throws Exception {
        Long resolvedUniversityId = universityId != null
                ? universityId
                : getUserId(registerUniversityAndGetToken("uni-for." + email, "Auto University for " + name));
        return registerAndGetToken("/api/v1/auth/register/club",
                new RegisterClubRequest(email, PASSWORD, name, resolvedUniversityId, null, null, null));
    }

    protected String registerEmployerAndGetToken(String email, String companyName) throws Exception {
        return registerAndGetToken("/api/v1/auth/register/employer",
                new RegisterEmployerRequest(email, PASSWORD, companyName, null, null, null, null, null));
    }

    protected String registerAdminAndGetToken(String email) throws Exception {
        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setVerified(true);
        admin.setFullName("Test Admin");
        adminRepository.save(admin);
        return login(email);
    }

    protected String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result, "/data/accessToken");
    }

    private String registerAndGetToken(String path, Object request) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result, "/data/accessToken");
    }

    protected Long getUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    // ── Community helpers ────────────────────────────────────────────────────

    protected Long createCommunity(String token, String name, CommunityVisibility visibility) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommunityRequest(name, "A test community", visibility, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected void joinCommunity(String token, Long communityId) throws Exception {
        mockMvc.perform(post(BASE + "/{communityId}/members", communityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    protected Long requestToJoin(String token, Long communityId, String message) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/{communityId}/join-requests", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"message\":\"%s\"}".formatted(message)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected void approveJoinRequest(String modToken, Long communityId, Long requestId) throws Exception {
        mockMvc.perform(post(BASE + "/{communityId}/join-requests/{requestId}/approve", communityId, requestId)
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk());
    }

    protected Long createAnnouncement(String modToken, Long communityId, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/{communityId}/announcements", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + modToken)
                        .content("{\"title\":\"%s\",\"content\":\"%s\"}".formatted(title, content)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected Long createCommunityPost(String memberToken, Long communityId, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/{communityId}/posts", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + memberToken)
                        .content(objectMapper.writeValueAsString(
                                new CreatePostRequest(title, content, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected String readJson(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).asText();
    }
}
