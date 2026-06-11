package com.unisphere.backend.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.AbstractIntegrationTest;
import com.unisphere.backend.identity.dto.RegisterStudentRequest;
import com.unisphere.backend.identity.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class UserControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/users";

    private String registerAndGetToken(String email, String matric) throws Exception {
        RegisterStudentRequest req = new RegisterStudentRequest(
                email, "Password123!", "Test User",
                matric, null, null, null,
                null, null, null, null, null
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    private Long getUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asLong();
    }

    // ── Update profile ────────────────────────────────────────────────────────

    @Test
    void updateProfile_withoutAuth_returns401() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(
                null, "0123456789", null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(put(BASE + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_phoneOnly_returnsUpdatedPhone() throws Exception {
        String token = registerAndGetToken("profile.phone@test.com", "USR1001");

        UpdateProfileRequest req = new UpdateProfileRequest(
                null, "0123456789", null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(put(BASE + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phone").value("0123456789"));
    }

    @Test
    void updateProfile_setAvatar_returnsNewAvatarUrl() throws Exception {
        String token = registerAndGetToken("profile.avatar@test.com", "USR1002");
        Long userId = getUserId(token);

        String avatarKey = "avatars/" + userId + "/test-avatar.jpg";
        UpdateProfileRequest req = new UpdateProfileRequest(
                avatarKey, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(put(BASE + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(avatarKey));
    }

    @Test
    void updateProfile_removeAvatar_setsAvatarUrlToNull() throws Exception {
        String token = registerAndGetToken("profile.removeavatar@test.com", "USR1003");
        Long userId = getUserId(token);

        // First set an avatar
        UpdateProfileRequest setReq = new UpdateProfileRequest(
                "avatars/" + userId + "/avatar.jpg", null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );
        mockMvc.perform(put(BASE + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(setReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("avatars/" + userId + "/avatar.jpg"));

        // Now remove it with empty string sentinel
        UpdateProfileRequest removeReq = new UpdateProfileRequest(
                "", null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(put(BASE + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(removeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void updateProfile_studentFields_updatesNameAndFaculty() throws Exception {
        String token = registerAndGetToken("profile.student@test.com", "USR1004");

        UpdateProfileRequest req = new UpdateProfileRequest(
                null, null, "Alice Tan", "Engineering", "Computer Science", 3,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(put(BASE + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Alice Tan"))
                .andExpect(jsonPath("$.data.faculty").value("Engineering"))
                .andExpect(jsonPath("$.data.program").value("Computer Science"))
                .andExpect(jsonPath("$.data.yearOfStudy").value(3));
    }

    @Test
    void updateProfile_nullFields_leavesExistingValuesUnchanged() throws Exception {
        String token = registerAndGetToken("profile.nullfields@test.com", "USR1005");

        // First set a phone number
        UpdateProfileRequest firstReq = new UpdateProfileRequest(
                null, "0111222333", null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );
        mockMvc.perform(put(BASE + "/me")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(firstReq)));

        // Then update only fullName — phone must stay unchanged
        UpdateProfileRequest secondReq = new UpdateProfileRequest(
                null, null, "Updated Name", null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(put(BASE + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(secondReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.phone").value("0111222333"));
    }

    @Test
    void updateProfile_verifiedByGetMe_persistsChange() throws Exception {
        String token = registerAndGetToken("profile.persist@test.com", "USR1006");

        UpdateProfileRequest req = new UpdateProfileRequest(
                null, "0199887766", null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );
        mockMvc.perform(put(BASE + "/me")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(req)));

        // Verify via GET /auth/me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("0199887766"));
    }
}
