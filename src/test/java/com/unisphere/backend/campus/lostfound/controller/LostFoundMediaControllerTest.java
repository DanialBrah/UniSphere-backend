package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.campus.lostfound.AbstractLostFoundIntegrationTest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundMediaPresignRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LostFoundMediaControllerTest extends AbstractLostFoundIntegrationTest {

    @Test
    void presign_returnsUploadUrlAndOwnedKey() throws Exception {
        String token = registerStudentAndGetToken("md1@test.com", "MD001");
        Long userId = getUserId(token);

        mockMvc.perform(post(BASE + "/media/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new LostFoundMediaPresignRequest("wallet.jpg", "image/jpeg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").exists())
                // Segregation is by key prefix, not by bucket.
                .andExpect(jsonPath("$.data.mediaKey").value(
                        org.hamcrest.Matchers.startsWith("lost-found/" + userId + "/")));
    }

    @Test
    void presign_unsupportedContentType_returns400() throws Exception {
        String token = registerStudentAndGetToken("md2@test.com", "MD002");

        mockMvc.perform(post(BASE + "/media/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new LostFoundMediaPresignRequest("payload.exe", "application/x-msdownload"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void presign_disallowedExtension_returns400() throws Exception {
        String token = registerStudentAndGetToken("md3@test.com", "MD003");

        mockMvc.perform(post(BASE + "/media/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new LostFoundMediaPresignRequest("wallet.svg", "image/png"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void upload_returnsKeyAndResolvedUrl() throws Exception {
        String token = registerStudentAndGetToken("md4@test.com", "MD004");
        Long userId = getUserId(token);
        MockMultipartFile file = new MockMultipartFile(
                "file", "wallet.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart(BASE + "/media/upload").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.data.mediaUrl").exists())
                .andExpect(jsonPath("$.data.mediaKey").value(
                        org.hamcrest.Matchers.startsWith("lost-found/" + userId + "/")));
    }

    @Test
    void deleteMedia_ownKey_succeeds() throws Exception {
        String token = registerStudentAndGetToken("md5@test.com", "MD005");
        Long userId = getUserId(token);

        mockMvc.perform(delete(BASE + "/media/lost-found/{userId}/mine.jpg", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMedia_anotherUsersKey_returns403() throws Exception {
        String owner = registerStudentAndGetToken("md6a@test.com", "MD006A");
        String other = registerStudentAndGetToken("md6b@test.com", "MD006B");
        Long ownerId = getUserId(owner);

        mockMvc.perform(delete(BASE + "/media/lost-found/{userId}/theirs.jpg", ownerId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void mediaEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(post(BASE + "/media/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LostFoundMediaPresignRequest("wallet.jpg", "image/jpeg"))))
                .andExpect(status().isUnauthorized());
    }
}
