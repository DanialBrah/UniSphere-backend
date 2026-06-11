package com.unisphere.backend.campus.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.campus.chatbot.dto.internal.ChatTurn;
import com.unisphere.backend.campus.chatbot.dto.request.ChatRequest;
import com.unisphere.backend.campus.chatbot.dto.response.ChatResponse;
import com.unisphere.backend.common.exception.ChatbotException;
import com.unisphere.backend.config.GeminiConfig;
import com.unisphere.backend.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final String CACHE_PREFIX   = "chatbot:cache:";
    private static final String SESSION_PREFIX = "chatbot:session:";
    private static final String SYSTEM_PROMPT  =
            "You are a helpful campus assistant for UniSphere, a university super-app. " +
            "Answer questions about campus life, academics, events, clubs, and student services. " +
            "Be concise, friendly, and accurate. If you don't know something specific to the campus, say so.";

    private final GeminiConfig geminiConfig;
    private final RestClient geminiRestClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ChatResponse chat(ChatRequest req, User currentUser) {
        String normalised = req.message().trim().toLowerCase().replaceAll("\\s+", " ");
        String cacheKey = CACHE_PREFIX + md5(normalised);

        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return new ChatResponse(cached, true, LocalDateTime.now());
        }

        List<ChatTurn> history = loadSessionHistory(currentUser.getId());

        String reply = callGemini(normalised, history);

        stringRedisTemplate.opsForValue().set(cacheKey, reply, geminiConfig.getCacheTtlHours(), TimeUnit.HOURS);

        appendToSession(currentUser.getId(), normalised, reply);

        return new ChatResponse(reply, false, LocalDateTime.now());
    }

    public void clearSession(Long userId) {
        stringRedisTemplate.delete(SESSION_PREFIX + userId);
    }

    public List<ChatTurn> getSessionHistory(Long userId) {
        return loadSessionHistory(userId);
    }

    // ── Gemini API ────────────────────────────────────────────────────────────

    private String callGemini(String userMessage, List<ChatTurn> history) {
        List<Map<String, Object>> contents = new ArrayList<>();

        for (ChatTurn turn : history) {
            contents.add(Map.of(
                    "role", turn.role(),
                    "parts", List.of(Map.of("text", turn.text()))
            ));
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMessage))
        ));

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", contents,
                "generationConfig", Map.of("temperature", 0.7, "maxOutputTokens", 512)
        );

        Map<?, ?> response = geminiRestClient.post()
                .uri("/v1beta/models/{model}:generateContent?key={key}",
                        geminiConfig.getModel(), geminiConfig.getApiKey())
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                    throw new ChatbotException("Gemini API rejected request: " + resp.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                    throw new ChatbotException("Gemini API unavailable: " + resp.getStatusCode());
                })
                .body(Map.class);

        try {
            List<?> candidates = (List<?>) response.get("candidates");
            Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            List<?> parts = (List<?>) content.get("parts");
            return (String) ((Map<?, ?>) parts.get(0)).get("text");
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            throw new ChatbotException("Failed to parse Gemini response");
        }
    }

    // ── Redis session ─────────────────────────────────────────────────────────

    private List<ChatTurn> loadSessionHistory(Long userId) {
        try {
            String key = SESSION_PREFIX + userId;
            List<String> entries = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (entries == null || entries.isEmpty()) return Collections.emptyList();
            return entries.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, ChatTurn.class);
                        } catch (JsonProcessingException e) {
                            return null;
                        }
                    })
                    .filter(t -> t != null)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load chatbot session for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void appendToSession(Long userId, String userMessage, String modelReply) {
        try {
            String key = SESSION_PREFIX + userId;
            int maxEntries = geminiConfig.getMaxHistoryTurns() * 2;
            String userJson = objectMapper.writeValueAsString(new ChatTurn("user", userMessage));
            String modelJson = objectMapper.writeValueAsString(new ChatTurn("model", modelReply));
            stringRedisTemplate.opsForList().rightPush(key, userJson);
            stringRedisTemplate.opsForList().rightPush(key, modelJson);
            stringRedisTemplate.opsForList().trim(key, -maxEntries, -1);
            stringRedisTemplate.expire(key, geminiConfig.getSessionTtlHours(), TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to persist chatbot session for user {}: {}", userId, e.getMessage());
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
