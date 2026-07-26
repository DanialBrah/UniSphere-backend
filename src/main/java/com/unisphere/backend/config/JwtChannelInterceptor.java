package com.unisphere.backend.config;

import com.unisphere.backend.common.ratelimit.RateLimitProperties;
import com.unisphere.backend.common.ratelimit.RateLimiterService;
import com.unisphere.backend.identity.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Validates the JWT once on STOMP CONNECT, then enforces a per-user rate limit on every SEND
 * frame afterwards. This is the only place that can rate-limit WebSocket traffic at all — once a
 * client is past the HTTP handshake, {@code IpRateLimitFilter}/{@code UserRateLimitFilter} never
 * see another byte of it, since the connection is upgraded and all further STOMP frames flow over
 * the same channel without going back through the servlet filter chain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            enforceRateLimit(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(token, userDetails)) {
                throw new MessageDeliveryException("Invalid or expired JWT token");
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            accessor.setUser(auth);

        } catch (MessageDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebSocket JWT validation failed: {}", e.getMessage());
            throw new MessageDeliveryException("Authentication failed: " + e.getMessage());
        }
    }

    private void enforceRateLimit(StompHeaderAccessor accessor) {
        if (!rateLimitProperties.isEnabled()) {
            return;
        }

        Principal user = accessor.getUser();
        if (user == null) {
            // Shouldn't happen — SEND only ever follows a successful CONNECT on the same
            // session — but fail closed on identity rather than silently skip the check.
            throw new MessageDeliveryException("Unauthenticated");
        }

        String key = "ratelimit:ws:" + user.getName();
        RateLimitProperties.Limit limit = rateLimitProperties.getWs();

        if (!rateLimiterService.tryConsume(key, limit)) {
            log.warn("WebSocket rate limit exceeded for key {}", key);
            throw new MessageDeliveryException("Too many messages — please slow down.");
        }
    }
}
