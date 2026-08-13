package com.unisphere.backend.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.identity.dto.LoginRequest;
import com.unisphere.backend.identity.dto.RegisterAlumniRequest;
import com.unisphere.backend.identity.dto.RegisterClubRequest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import com.unisphere.backend.identity.dto.RegisterUniversityRequest;
import com.unisphere.backend.identity.entity.Admin;
import com.unisphere.backend.identity.entity.UserStatus;
import com.unisphere.backend.identity.repository.AdminRepository;
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
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for all projects integration tests. Mocks the same infra AbstractCommunityIntegrationTest
 * does (STOMP push, Redis counters, S3 presigning) so tests only need the shared MySQL container —
 * project cover-image keys resolve through the same MediaUrlResolver every other module uses.
 */
public abstract class AbstractProjectIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123!";
    protected static final String BASE = "/api/v1/projects";

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
    void setUpProjectMocks() throws Exception {
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
                .thenReturn(URI.create("https://mock-storage.example.com/posts/projects/test.jpg").toURL());
        Mockito.lenient().when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(fakePut);

        PresignedGetObjectRequest fakeGet = Mockito.mock(PresignedGetObjectRequest.class);
        Mockito.lenient().when(fakeGet.url())
                .thenReturn(URI.create("https://mock-storage.example.com/posts/projects/test.jpg?get").toURL());
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

    // ── Project helpers ──────────────────────────────────────────────────────

    protected Long createProject(String ownerToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"title\":\"%s\",\"description\":\"A test project\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected void setRecruiting(String ownerToken, Long projectId, boolean recruiting) throws Exception {
        mockMvc.perform(put(BASE + "/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"isRecruiting\":%s}".formatted(recruiting)))
                .andExpect(status().isOk());
    }

    protected Long addRole(String ownerToken, Long projectId, String title, int slots) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/{projectId}/roles", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"title\":\"%s\",\"slots\":%d}".formatted(title, slots)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected Long apply(String applicantToken, Long projectId, Long roleId, String message) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/{projectId}/roles/{roleId}/applications", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + applicantToken)
                        .content("{\"message\":\"%s\"}".formatted(message)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected String readJson(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).asText();
    }
}
