package com.unisphere.backend.config;

import com.unisphere.backend.common.ratelimit.RateLimitProperties;
import com.unisphere.backend.common.ratelimit.RateLimiterService;
import com.unisphere.backend.identity.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    JwtService jwtService;

    @Mock
    UserDetailsService userDetailsService;

    @Mock
    RateLimiterService rateLimiterService;

    RateLimitProperties properties;
    JwtChannelInterceptor interceptor;

    private static final UserDetails ALICE = new User("alice@test.com", "password", List.of());

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setWs(new RateLimitProperties.Limit(60, 60));
        interceptor = new JwtChannelInterceptor(jwtService, userDetailsService, rateLimiterService, properties);
    }

    @Test
    void connect_validToken_setsUserOnAccessor() {
        when(jwtService.extractEmail("valid-token")).thenReturn("alice@test.com");
        when(userDetailsService.loadUserByUsername("alice@test.com")).thenReturn(ALICE);
        when(jwtService.isTokenValid("valid-token", ALICE)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, null);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("alice@test.com");
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void connect_missingAuthorizationHeader_throwsWithoutTouchingRateLimiter() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void send_underLimit_allowsMessageThrough() {
        when(rateLimiterService.tryConsume("ratelimit:ws:alice@test.com", properties.getWs()))
                .thenReturn(true);

        Message<byte[]> message = sendMessageFrom(ALICE);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    @Test
    void send_overLimit_throwsAndBlocksMessage() {
        when(rateLimiterService.tryConsume("ratelimit:ws:alice@test.com", properties.getWs()))
                .thenReturn(false);

        Message<byte[]> message = sendMessageFrom(ALICE);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void send_rateLimitingDisabled_skipsCheckEntirely() {
        properties.setEnabled(false);

        Message<byte[]> message = sendMessageFrom(ALICE);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void send_noAuthenticatedUser_throwsWithoutConsultingRateLimiter() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(rateLimiterService);
    }

    private Message<byte[]> sendMessageFrom(UserDetails principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
