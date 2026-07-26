package com.unisphere.backend.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpRateLimitFilterTest {

    @Mock
    RateLimiterService rateLimiterService;

    RateLimitProperties properties;
    IpRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setAuth(new RateLimitProperties.Limit(5, 60));
        filter = new IpRateLimitFilter(rateLimiterService, properties, new ObjectMapper());
    }

    @Test
    void authPath_underLimit_allowsRequestThrough() throws Exception {
        when(rateLimiterService.tryConsume(anyString(), eq(properties.getAuth()))).thenReturn(true);

        MockHttpServletRequest request = postRequest("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void authPath_overLimit_returns429AndBlocksChain() throws Exception {
        when(rateLimiterService.tryConsume(anyString(), eq(properties.getAuth()))).thenReturn(false);
        when(rateLimiterService.retryAfterSeconds(anyString(), eq(properties.getAuth()))).thenReturn(42L);

        MockHttpServletRequest request = postRequest("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void protectedPath_neverConsultsRateLimiter() throws Exception {
        MockHttpServletRequest request = postRequest("/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiterService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void publicInfraPath_getsSameStrictTierAsAuthPaths() throws Exception {
        // swagger, api-docs, /ws are all public — every one of them gets the same strict
        // IP-keyed limit as the login/register endpoints, not just the auth ones. /api/health is
        // the one deliberate exception (see healthEndpoint_alwaysExempt below).
        when(rateLimiterService.tryConsume(anyString(), eq(properties.getAuth()))).thenReturn(false);
        when(rateLimiterService.retryAfterSeconds(anyString(), eq(properties.getAuth()))).thenReturn(30L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui.html");
        request.setServletPath("/swagger-ui.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(rateLimiterService).tryConsume(anyString(), eq(properties.getAuth()));
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void healthEndpoint_alwaysExempt_evenThoughItsInPublicPaths() throws Exception {
        // Global (Redis-shared) counter would otherwise get hit by every scaled instance's
        // health probe against the same budget — not a meaningful attack surface either.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.setServletPath("/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiterService);
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void optionsPreflight_skipsEntirely() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiterService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void disabled_skipsEntirelyEvenOnAuthPath() throws Exception {
        properties.setEnabled(false);

        MockHttpServletRequest request = postRequest("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiterService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void usesLastForwardedForHop_asKey() throws Exception {
        when(rateLimiterService.tryConsume(eq("ratelimit:ip:203.0.113.4"), eq(properties.getAuth())))
                .thenReturn(true);

        MockHttpServletRequest request = postRequest("/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "9.9.9.9-attacker-supplied, 203.0.113.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(rateLimiterService).tryConsume("ratelimit:ip:203.0.113.4", properties.getAuth());
    }

    private MockHttpServletRequest postRequest(String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", servletPath);
        request.setServletPath(servletPath);
        return request;
    }
}
