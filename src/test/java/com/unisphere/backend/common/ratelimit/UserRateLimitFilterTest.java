package com.unisphere.backend.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRateLimitFilterTest {

    @Mock
    RateLimiterService rateLimiterService;

    RateLimitProperties properties;
    UserRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setApi(new RateLimitProperties.Limit(120, 60));
        RateLimitProperties.PathOverride chatbotOverride = new RateLimitProperties.PathOverride();
        chatbotOverride.setPathPattern("/api/v1/campus/chatbot/**");
        chatbotOverride.setLimit(20);
        chatbotOverride.setWindowSeconds(60);
        properties.setOverrides(List.of(chatbotOverride));
        filter = new UserRateLimitFilter(rateLimiterService, properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedRequest_keyedByUsername_notIp() throws Exception {
        authenticateAs("alice@test.com");
        when(rateLimiterService.tryConsume(eq("ratelimit:user:u:alice@test.com"), eq(properties.getApi())))
                .thenReturn(true);

        MockHttpServletRequest request = getRequest("/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(rateLimiterService).tryConsume("ratelimit:user:u:alice@test.com", properties.getApi());
        verify(chain).doFilter(request, response);
    }

    @Test
    void unauthenticatedRequest_onProtectedPath_fallsBackToIp() throws Exception {
        // e.g. a missing/invalid token on a protected endpoint — Spring Security's own
        // authorization check rejects it shortly after, but this filter runs first.
        when(rateLimiterService.tryConsume(eq("ratelimit:user:ip:127.0.0.1"), eq(properties.getApi())))
                .thenReturn(true);

        MockHttpServletRequest request = getRequest("/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(rateLimiterService).tryConsume("ratelimit:user:ip:127.0.0.1", properties.getApi());
    }

    @Test
    void publicInfraPath_skippedEntirely_leftToIpRateLimitFilter() throws Exception {
        // /api/health, swagger, api-docs, /ws — all now handled exclusively by IpRateLimitFilter
        MockHttpServletRequest request = getRequest("/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiterService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void chatbotPath_usesOverrideLimit_notDefaultApiLimit() throws Exception {
        authenticateAs("bob@test.com");
        RateLimitProperties.Limit chatbotLimit = properties.getOverrides().get(0);
        when(rateLimiterService.tryConsume(anyString(), eq(chatbotLimit))).thenReturn(true);

        MockHttpServletRequest request = getRequest("/api/v1/campus/chatbot/ask");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(rateLimiterService).tryConsume(anyString(), eq(chatbotLimit));
        verify(rateLimiterService, never()).tryConsume(anyString(), eq(properties.getApi()));
    }

    @Test
    void authEndpoint_skippedEntirely_leftToIpRateLimitFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiterService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void overLimit_returns429WithRetryAfter() throws Exception {
        authenticateAs("alice@test.com");
        when(rateLimiterService.tryConsume(anyString(), any())).thenReturn(false);
        when(rateLimiterService.retryAfterSeconds(anyString(), any())).thenReturn(15L);

        MockHttpServletRequest request = getRequest("/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("15");
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void disabled_skipsEntirely() throws Exception {
        properties.setEnabled(false);

        MockHttpServletRequest request = getRequest("/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiterService);
        verify(chain).doFilter(request, response);
    }

    private void authenticateAs(String email) {
        User principal = new User(email, "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private MockHttpServletRequest getRequest(String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", servletPath);
        request.setServletPath(servletPath);
        return request;
    }
}
