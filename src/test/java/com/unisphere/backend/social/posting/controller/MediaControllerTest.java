package com.unisphere.backend.social.posting.controller;

import com.unisphere.backend.social.posting.AbstractPostingIntegrationTest;
import com.unisphere.backend.social.posting.dto.request.MediaPresignRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class MediaControllerTest extends AbstractPostingIntegrationTest {

    private static final String BASE = "/api/v1/media";

    // ── Presign upload ────────────────────────────────────────────────────────

    @Test
    void presignUpload_validImageFile_returns200WithUrl() throws Exception {
        String token = registerStudentAndGetToken("media.presign@test.com", "MAT3001");

        MediaPresignRequest req = new MediaPresignRequest("profile.jpg", "image/jpeg");

        mockMvc.perform(post(BASE + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.mediaKey").isNotEmpty());
    }

    @Test
    void presignUpload_validVideoFile_returns200() throws Exception {
        String token = registerStudentAndGetToken("media.video@test.com", "MAT3002");

        MediaPresignRequest req = new MediaPresignRequest("clip.mp4", "video/mp4");

        mockMvc.perform(post(BASE + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaKey").value(
                        org.hamcrest.Matchers.containsString(".mp4")));
    }

    @Test
    void presignUpload_invalidFileExtension_returns400() throws Exception {
        String token = registerStudentAndGetToken("media.invalid@test.com", "MAT3003");

        // .exe is not in ALLOWED_EXTENSIONS
        MediaPresignRequest req = new MediaPresignRequest("malware.exe", "image/jpeg");

        mockMvc.perform(post(BASE + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void presignUpload_withoutAuth_returns401() throws Exception {
        MediaPresignRequest req = new MediaPresignRequest("photo.jpg", "image/jpeg");

        mockMvc.perform(post(BASE + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── Delete media ──────────────────────────────────────────────────────────

    @Test
    void deleteMedia_ownKey_returns200() throws Exception {
        String token = registerStudentAndGetToken("media.delete@test.com", "MAT3004");

        // Get the user's ID from /me so we can build a valid key
        MvcResult meResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/auth/me")
                                .header("Authorization", "Bearer " + token))
                .andReturn();
        Long userId = objectMapper.readTree(meResult.getResponse().getContentAsString())
                .at("/data/id").asLong();

        // mediaKey must start with posts/{userId}/
        String mediaKey = "posts/" + userId + "/test-photo.jpg";

        mockMvc.perform(delete(BASE)
                        .param("mediaKey", mediaKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteMedia_otherUsersKey_returns403() throws Exception {
        String token = registerStudentAndGetToken("media.forbidden@test.com", "MAT3005");

        // Key belongs to user 99999, not the current user
        String otherUsersKey = "posts/99999/stolen-photo.jpg";

        mockMvc.perform(delete(BASE)
                        .param("mediaKey", otherUsersKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

