package com.unisphere.backend.campus.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.campus.news.dto.request.CreateNewsArticleRequest;
import com.unisphere.backend.campus.news.dto.request.NewsStatusUpdateRequest;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
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

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for all campus/news integration tests. Mocks Redis and object storage so tests only need
 * the shared MySQL container.
 *
 * <p>The {@code presignGetObject} stub is not optional here: every article response presigns a
 * cover image and an author avatar, so an unstubbed presigner makes {@code toViewableUrl} return
 * null and the URL assertions fail for a reason that has nothing to do with the code under test.
 */
public abstract class AbstractNewsIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123!";
    protected static final String BASE = "/api/v1/news";

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

    /**
     * For driving {@code @Modifying} repository methods directly. Those need an active transaction,
     * which in production their calling service supplies; a test calling one straight has none.
     */
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
    void setUpNewsMocks() throws Exception {
        ValueOperations<String, Long> ops = Mockito.mock(ValueOperations.class);
        Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(ops);
        Mockito.lenient().when(ops.get(anyString())).thenReturn(null);
        Mockito.lenient().when(ops.increment(anyString())).thenReturn(1L);
        Mockito.lenient().when(ops.increment(anyString(), Mockito.anyLong())).thenReturn(1L);
        Mockito.lenient().when(ops.decrement(anyString())).thenReturn(0L);
        Mockito.lenient().when(ops.decrement(anyString(), Mockito.anyLong())).thenReturn(0L);
        Mockito.lenient().when(redisTemplate.keys(anyString())).thenReturn(Set.of());

        // Typed matchers to resolve the S3Client overload ambiguity
        Mockito.lenient().doReturn(HeadBucketResponse.builder().build())
                .when(s3Client).headBucket(any(Consumer.class));
        Mockito.lenient().doReturn(DeleteObjectResponse.builder().build())
                .when(s3Client).deleteObject(any(Consumer.class));

        PresignedPutObjectRequest fakePut = Mockito.mock(PresignedPutObjectRequest.class);
        Mockito.lenient().when(fakePut.url())
                .thenReturn(URI.create("https://mock-storage.example.com/posts/news/1/test.jpg").toURL());
        Mockito.lenient().when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(fakePut);

        PresignedGetObjectRequest fakeGet = Mockito.mock(PresignedGetObjectRequest.class);
        Mockito.lenient().when(fakeGet.url())
                .thenReturn(URI.create("https://mock-storage.example.com/posts/news/1/test.jpg?get").toURL());
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
        return registerAndGetToken("/api/v1/auth/register/club",
                new RegisterClubRequest(email, PASSWORD, name, universityId, null, null, null));
    }

    protected String registerEmployerAndGetToken(String email, String companyName) throws Exception {
        return registerAndGetToken("/api/v1/auth/register/employer",
                new RegisterEmployerRequest(email, PASSWORD, companyName, null, null, null, null, null));
    }

    /**
     * There is no public admin registration endpoint, so the row is inserted directly and then
     * logged in through the real login path to obtain a genuine token.
     */
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

    protected void setStudentUniversityId(Long userId, Long universityId) {
        Student student = studentRepository.findById(userId).orElseThrow();
        student.setUniversityId(universityId);
        studentRepository.save(student);
    }

    // ── News helpers ──────────────────────────────────────────────────────────

    protected Long createArticle(String token, String title, String content,
                                 NewsCategory category, NewsVisibility visibility,
                                 NewsStatus status) throws Exception {
        return createArticle(token, new CreateNewsArticleRequest(
                title, "A summary", content, category, visibility, status,
                null, null, null, null));
    }

    protected Long createArticle(String token, CreateNewsArticleRequest req) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    /** A published PUBLIC article with tags, the usual starting point for read-path tests. */
    protected Long createPublishedArticle(String token, String title, String content,
                                          List<String> tags) throws Exception {
        return createArticle(token, new CreateNewsArticleRequest(
                title, "A summary", content, NewsCategory.GENERAL, NewsVisibility.PUBLIC,
                NewsStatus.PUBLISHED, null, null, tags, null));
    }

    protected void changeStatus(String token, Long articleId, NewsStatus status) throws Exception {
        mockMvc.perform(patch(BASE + "/{articleId}/status", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new NewsStatusUpdateRequest(status, null))))
                .andExpect(status().isOk());
    }

    protected Long createNewsComment(String token, Long articleId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/{articleId}/comments", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(readJson(result, "/data/id"));
    }

    protected String readJson(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).asText();
    }
}
