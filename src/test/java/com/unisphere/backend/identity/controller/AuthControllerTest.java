package com.unisphere.backend.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.identity.dto.LoginRequest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import com.unisphere.backend.identity.util.RefreshTokenCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional  // rolls back after each test — keeps container DB clean between runs
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/auth";
    private static final String REFRESH_COOKIE = RefreshTokenCookie.NAME;

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    void registerStudent_validRequest_returns201WithToken() throws Exception {
        RegisterStudentRequest req = new RegisterStudentRequest(
                "new.student@test.com", "Password123!", "Alice Tan",
                "2021001111", null, null, "+60123456789",
                "CS", "Computer Science", 1, null, null
        );

        mockMvc.perform(post(BASE + "/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.email").value("new.student@test.com"));
    }

    @Test
    void registerStudent_duplicateEmail_returns409() throws Exception {
        RegisterStudentRequest req = new RegisterStudentRequest(
                "duplicate@test.com", "Password123!", "Bob Lim",
                "2021002222", null, null, null,
                null, null, null, null, null
        );

        // First registration
        mockMvc.perform(post(BASE + "/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Duplicate
        mockMvc.perform(post(BASE + "/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void registerStudent_missingRequiredFields_returns400() throws Exception {
        String body = """
                {"email":"bad-email","password":"short"}
                """;

        mockMvc.perform(post(BASE + "/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        // Register first
        RegisterStudentRequest reg = new RegisterStudentRequest(
                "login.test@test.com", "Password123!", "Charlie",
                "2021003333", null, null, null,
                null, null, null, null, null
        );
        mockMvc.perform(post(BASE + "/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // Login
        LoginRequest login = new LoginRequest("login.test@test.com", "Password123!");
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("login.test@test.com"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        // Register first
        RegisterStudentRequest reg = new RegisterStudentRequest(
                "wrongpass@test.com", "Password123!", "Dave",
                "2021004444", null, null, null,
                null, null, null, null, null
        );
        mockMvc.perform(post(BASE + "/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // Wrong password
        LoginRequest login = new LoginRequest("wrongpass@test.com", "WrongPassword!");
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        LoginRequest login = new LoginRequest("nobody@test.com", "Password123!");
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    // ── Me ────────────────────────────────────────────────────────────────────

    @Test
    void me_withValidToken_returns200WithProfile() throws Exception {
        // Register and extract token
        RegisterStudentRequest reg = new RegisterStudentRequest(
                "me.test@test.com", "Password123!", "Eve",
                "2021005555", null, null, null,
                null, null, null, null, null
        );
        MvcResult result = mockMvc.perform(post(BASE + "/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        mockMvc.perform(get(BASE + "/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("me.test@test.com"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/me")
                        .header("Authorization", "Bearer this.is.not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    // ── Refresh-token cookie ──────────────────────────────────────────────────
    // The whole point of the cookie is that JS can never read the refresh token. These lock in
    // both halves of that: it must be in an HttpOnly cookie, and it must NOT be in the body.

    @Test
    void login_putsRefreshTokenInHttpOnlyCookie_andNeverInResponseBody() throws Exception {
        register("cookie.login@test.com", "2021006666", "Frank");

        MvcResult result = mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("cookie.login@test.com", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().value(REFRESH_COOKIE, not(emptyString())))
                // Scoped so the browser never attaches it to non-auth API calls
                .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");
    }

    @Test
    void register_putsRefreshTokenInHttpOnlyCookie_andNeverInResponseBody() throws Exception {
        RegisterStudentRequest req = new RegisterStudentRequest(
                "cookie.register@test.com", "Password123!", "Grace",
                "2021007777", null, null, null, null, null, null, null, null);

        mockMvc.perform(post(BASE + "/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void refresh_withCookie_issuesNewAccessTokenAndRotatesCookie() throws Exception {
        MvcResult registered = registerAndReturn("cookie.refresh@test.com", "2021008888", "Heidi");
        Cookie cookie = registered.getResponse().getCookie(REFRESH_COOKIE);

        MvcResult refreshed = mockMvc.perform(post(BASE + "/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andReturn();

        // Rotation: the old token is single-use, so the reissued cookie must differ
        assertThat(refreshed.getResponse().getCookie(REFRESH_COOKIE))
                .isNotNull()
                .extracting(Cookie::getValue)
                .isNotEqualTo(cookie.getValue());
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_afterLogout_isRejected_andLogoutClearsTheCookie() throws Exception {
        MvcResult registered = registerAndReturn("cookie.logout@test.com", "2021009999", "Ivan");
        Cookie cookie = registered.getResponse().getCookie(REFRESH_COOKIE);

        mockMvc.perform(post(BASE + "/logout").cookie(cookie))
                .andExpect(status().isOk())
                // maxAge 0 is what actually removes it from the browser
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        // Server-side revocation, not just a cleared cookie — replaying the stolen value fails
        mockMvc.perform(post(BASE + "/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutCookie_stillSucceeds() throws Exception {
        // The client can't read an HttpOnly cookie, so it can't know whether a session exists —
        // logging out of an already-dead one must still reach a signed-out state.
        mockMvc.perform(post(BASE + "/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void register(String email, String matric, String name) throws Exception {
        registerAndReturn(email, matric, name);
    }

    private MvcResult registerAndReturn(String email, String matric, String name) throws Exception {
        RegisterStudentRequest req = new RegisterStudentRequest(
                email, "Password123!", name, matric, null, null, null, null, null, null, null, null);
        return mockMvc.perform(post(BASE + "/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
