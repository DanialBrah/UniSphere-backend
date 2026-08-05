package com.unisphere.backend.campus.news.controller;

import com.unisphere.backend.campus.news.AbstractNewsIntegrationTest;
import com.unisphere.backend.campus.news.dto.request.NewsMediaPresignRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsMediaControllerTest extends AbstractNewsIntegrationTest {

    private static final String MEDIA = BASE + "/media";

    @Test
    void presignReturnsAKeyScopedToTheCaller() throws Exception {
        String university = registerUniversityAndGetToken("nm.uni.presign@test.com", "Presign University");
        Long userId = getUserId(university);

        mockMvc.perform(post(MEDIA + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content(objectMapper.writeValueAsString(
                                new NewsMediaPresignRequest("cover.jpg", "image/jpeg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.mediaKey",
                        matchesPattern("^news/" + userId + "/[0-9a-f-]+\\.jpg$")));
    }

    @Test
    void disallowedExtensionIsRejected() throws Exception {
        String university = registerUniversityAndGetToken("nm.uni.ext@test.com", "Ext University");

        mockMvc.perform(post(MEDIA + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content("{\"filename\":\"payload.exe\",\"contentType\":\"image/png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void unsupportedContentTypeFailsValidation() throws Exception {
        String university = registerUniversityAndGetToken("nm.uni.ct@test.com", "ContentType University");

        mockMvc.perform(post(MEDIA + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + university)
                        .content("{\"filename\":\"cover.jpg\",\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deletingSomeoneElsesKeyIsForbidden() throws Exception {
        String university = registerUniversityAndGetToken("nm.uni.del@test.com", "Del University");

        mockMvc.perform(delete(MEDIA + "/news/999999/not-yours.jpg")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void deletingAPostsKeyThroughTheNewsEndpointIsForbidden() throws Exception {
        String university = registerUniversityAndGetToken("nm.uni.cross@test.com", "Cross University");
        Long userId = getUserId(university);

        // Even the caller's own posts/ key is out of scope here — this endpoint owns news/ only.
        mockMvc.perform(delete(MEDIA + "/posts/" + userId + "/mine.jpg")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void deletingOwnKeySucceeds() throws Exception {
        String university = registerUniversityAndGetToken("nm.uni.own@test.com", "Own University");
        Long userId = getUserId(university);

        mockMvc.perform(delete(MEDIA + "/news/" + userId + "/mine.jpg")
                        .header("Authorization", "Bearer " + university))
                .andExpect(status().isOk());
    }
}
