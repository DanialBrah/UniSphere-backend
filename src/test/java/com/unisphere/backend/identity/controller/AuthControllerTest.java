package com.unisphere.backend.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.identity.dto.LoginRequest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional  // rolls back after each test — keeps DB clean between runs
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/auth";

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
}
