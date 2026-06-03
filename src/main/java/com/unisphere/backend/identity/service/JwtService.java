package com.unisphere.backend.identity.service;

import com.unisphere.backend.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String TYPE_ACCESS  = "access";
    private static final String TYPE_REFRESH = "refresh";

    private SecretKey getSigningKey() {
        byte[] keyBytes = HexFormat.of().parseHex(jwtConfig.getSecret());
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public String generateToken(UserDetails userDetails) {
        return buildToken(userDetails, jwtConfig.getExpiration(), TYPE_ACCESS);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails, jwtConfig.getRefreshExpiration(), TYPE_REFRESH);
    }

    private String buildToken(UserDetails userDetails, long expiration, String tokenType) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", userDetails.getAuthorities().iterator().next()
                        .getAuthority().replace("ROLE_", ""))
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /** True only when the token is a valid, unexpired access token for the given user. */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername())
                    && !isTokenExpired(token)
                    && TYPE_ACCESS.equals(extractTokenType(token));
        } catch (JwtException e) {
            return false;
        }
    }

    /** True only when the token is a valid, unexpired refresh token for the given user. */
    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername())
                    && !isTokenExpired(token)
                    && TYPE_REFRESH.equals(extractTokenType(token));
        } catch (JwtException e) {
            return false;
        }
    }

    /** True only when the token carries the refresh token_type claim. */
    public boolean isRefreshToken(String token) {
        try {
            return TYPE_REFRESH.equals(extractTokenType(token));
        } catch (JwtException e) {
            return false;
        }
    }

    /** Extracts token expiration as a java.time.Instant for DB storage. */
    public java.time.Instant extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration).toInstant();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}
