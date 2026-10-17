package com.ninepaws.backend.services.impl;

import com.ninepaws.backend.constants.RedisKeyConstants;
import com.ninepaws.backend.services.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationServiceImpl implements TokenRevocationService {

    private static final String BLACKLIST_MARKER = "REVOKED";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void recordRefreshToken(UUID userId, String jti, String token, long ttlSeconds) {
        String refreshKey = RedisKeyConstants.refreshTokenKey(userId, jti);
        redisTemplate.opsForValue().set(refreshKey, token, Duration.ofSeconds(ttlSeconds));

        String sessionKey = RedisKeyConstants.userSessionsKey(userId);
        redisTemplate.opsForSet().add(sessionKey, jti);
        log.debug("Recorded refresh token with jti: {} for user: {}", jti, userId);
    }

    @Override
    public boolean isRefreshTokenValid(UUID userId, String jti) {
        String refreshKey = RedisKeyConstants.refreshTokenKey(userId, jti);
        return Boolean.TRUE.equals(redisTemplate.hasKey(refreshKey));
    }

    @Override
    public void revokeRefreshToken(UUID userId, String jti) {
        String refreshKey = RedisKeyConstants.refreshTokenKey(userId, jti);
        redisTemplate.delete(refreshKey);

        String sessionKey = RedisKeyConstants.userSessionsKey(userId);
        redisTemplate.opsForSet().remove(sessionKey, jti);
        log.debug("Revoked refresh token with jti: {} for user: {}", jti, userId);
    }

    @Override
    public void blacklistAccessToken(String jti, long remainingExpirationMs) {
        if (remainingExpirationMs <= 0) {
            log.debug("Skipping blacklisting for token with jti: {} as remaining TTL is non-positive: {}ms", jti, remainingExpirationMs);
            return;
        }

        String blacklistKey = RedisKeyConstants.tokenBlacklistKey(jti);
        redisTemplate.opsForValue().set(blacklistKey, BLACKLIST_MARKER, Duration.ofMillis(remainingExpirationMs));
        log.debug("Blacklisted access token with jti: {} for {}ms", jti, remainingExpirationMs);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        String blacklistKey = RedisKeyConstants.tokenBlacklistKey(jti);
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
    }

    @Override
    public void revokeAllUserSessions(UUID userId) {
        String sessionKey = RedisKeyConstants.userSessionsKey(userId);
        Set<String> jtis = redisTemplate.opsForSet().members(sessionKey);
        if (jtis != null && !jtis.isEmpty()) {
            for (String jti : jtis) {
                redisTemplate.delete(RedisKeyConstants.refreshTokenKey(userId, jti));
            }
        }
        redisTemplate.delete(sessionKey);
        log.debug("Revoked all sessions for user: {}", userId);
    }
}
