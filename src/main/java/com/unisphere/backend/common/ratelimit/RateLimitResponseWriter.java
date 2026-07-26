package com.unisphere.backend.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisphere.backend.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

final class RateLimitResponseWriter {

    private RateLimitResponseWriter() {
    }

    static void writeTooManyRequests(HttpServletResponse response, ObjectMapper objectMapper, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("RATE_LIMIT_EXCEEDED",
                        "Too many requests. Please try again in " + retryAfterSeconds + " seconds.")));
    }
}
