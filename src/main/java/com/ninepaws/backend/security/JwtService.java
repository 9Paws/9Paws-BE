package com.ninepaws.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    @Autowired
    public JwtService(
            @Value("${app.jwt.secret}") String secretBase64,
            @Value("${app.jwt.access-token-expiration:1800000}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs
    ) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException("JWT secret cannot be null or blank");
        }
        byte[] keyBytes = Decoders.BASE64.decode(secretBase64);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public JwtService(String secretBase64) {
        this(secretBase64, 1800000L, 604800000L);
    }

    public String generateAccessToken(UUID userId, String email, String jti) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .claim("jti", jti)
                .claim("email", email)
                .claim("type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId, String jti) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .claim("jti", jti)
                .claim("type", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public TokenPayload parseAndValidateToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();
        UUID userId = (subject != null) ? UUID.fromString(subject) : null;
        String email = claims.get("email", String.class);
        String jti = claims.getId() != null ? claims.getId() : claims.get("jti", String.class);
        String tokenType = claims.get("type", String.class) != null
                ? claims.get("type", String.class)
                : claims.get("tokenType", String.class);
        Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
        Instant expiresAt = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;

        return new TokenPayload(userId, email, jti, tokenType, issuedAt, expiresAt);
    }

    public long getRemainingExpirationMs(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiration = claims.getExpiration();
            if (expiration == null) {
                return 0L;
            }
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0L, remaining);
        } catch (ExpiredJwtException e) {
            return 0L;
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationMs / 1000;
    }
}
