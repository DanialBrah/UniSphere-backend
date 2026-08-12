package com.unisphere.backend.campus.lostfound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundClaimRequest;
import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundClaimDecisionRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundStatusUpdateRequest;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import com.unisphere.backend.identity.dto.LoginRequest;
import com.unisphere.backend.identity.dto.RegisterAlumniRequest;
import com.unisphere.backend.identity.dto.RegisterClubRequest;
import com.unisphere.backend.identity.dto.RegisterEmployerRequest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import com.unisphere.backend.identity.dto.RegisterUniversityRequest;
import com.unisphere.backend.identity.entity.Admin;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.UserStatus;
import com.unisphere.backend.identity.repository.AdminRepository;
import com.unisphere.backend.identity.repository.StudentRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for all campus/lostfound integration tests. Mocks the same infra the news and community
 * bases do (STOMP push, Redis counters, S3 presigning) so tests only need the shared MySQL
 * container.
 *
 * <p>The {@code presignGetObject} stub is not optional: every item response presigns a primary
 * image and a reporter avatar, so an unstubbed presigner makes {@code toViewableUrl} return null
 * and URL assertions fail for reasons unrelated to the code under test.
 */
public abstract class AbstractLostFoundIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123!";
    protected static final String BASE = "/api/v1/lost-found";

    /** Campus reference point, consistent with LostFoundSeeder. */
    protected static final BigDecimal CAMPUS_LAT = new BigDecimal("3.0678000");
    protected static final BigDecimal CAMPUS_LNG = new BigDecimal("101.5006000");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AdminRepository adminRepository;

    @Autowired
    protected StudentRepository studentRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Needed to drive the bulk expiry update, which requires an active transaction. */
    @Autowired
    protected TransactionTemplate transactionTemplate;

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
    void setUpLostFoundMocks() throws Exception {
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
                .thenReturn(URI.create("https://mock-storage.example.com/posts/lost-found/test.jpg").toURL());
        Mockito.lenient().when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(fakePut);

        PresignedGetObjectRequest fakeGet = Mockito.mock(PresignedGetObjectRequest.class);
        Mockito.lenient().when(fakeGet.url())
                .thenReturn(URI.create("https://mock-storage.example.com/posts/lost-found/test.jpg?get").toURL());
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

    /** Students register without an affiliation; university scoping tests need one. */
    protected void setStudentUniversityId(Long userId, Long universityId) {
        Student student = studentRepository.findById(userId).orElseThrow();
        student.setUniversityId(universityId);
        studentRepository.save(student);
    }

    // ── Lost & Found helpers ─────────────────────────────────────────────────

    protected Long reportLost(String token, String title, BigDecimal lat, BigDecimal lng) throws Exception {
        return reportItem(token, new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.ELECTRONICS, title,
                "A test description for " + title, null, null,
                "Test incident place", lat, lng,
                null, null, null, null,
                LocalDateTime.now().minusDays(1), null));
    }

    protected Long reportFound(String token, String title, BigDecimal lat, BigDecimal lng,
                               String pickupPlace) throws Exception {
        return reportItem(token, new CreateLostFoundItemRequest(
                LostFoundItemType.FOUND, LostFoundCategory.ELECTRONICS, title,
                "A test description for " + title, "The withheld secret detail", null,
                "Test incident place", lat, lng,
                pickupPlace, lat, lng, "Test pickup instructions",
                LocalDateTime.now().minusDays(1), null));
    }

    protected Long reportItem(String token, CreateLostFoundItemRequest req) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected Long submitClaim(String token, Long itemId, String proofText) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/items/{itemId}/claims", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateLostFoundClaimRequest(proofText, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected void decideClaim(String token, Long claimId, LostFoundClaimStatus status) throws Exception {
        mockMvc.perform(patch(BASE + "/claims/{claimId}", claimId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new LostFoundClaimDecisionRequest(status, "Decided by test"))))
                .andExpect(status().isOk());
    }

    protected void changeItemStatus(String token, Long itemId, LostFoundItemStatus status) throws Exception {
        mockMvc.perform(patch(BASE + "/items/{itemId}/status", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new LostFoundStatusUpdateRequest(status, null))))
                .andExpect(status().isOk());
    }

    /** A claim submitted then approved in one step, for tests that need an approved claimant. */
    protected Long submitAndApproveClaim(String claimantToken, String reporterToken, Long itemId)
            throws Exception {
        Long claimId = submitClaim(claimantToken, itemId, "This is definitely mine, here is my proof.");
        decideClaim(reporterToken, claimId, LostFoundClaimStatus.APPROVED);
        return claimId;
    }

    protected String readJson(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).asText();
    }

    protected boolean isJsonNull(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).isMissingNode()
                || objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).isNull();
    }

    protected List<String> emptyList() {
        return List.of();
    }
}
