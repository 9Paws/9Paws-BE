package com.ninepaws.backend.services;

import java.util.UUID;

public interface TokenRevocationService {

    void recordRefreshToken(UUID userId, String jti, String token, long ttlSeconds);

    boolean isRefreshTokenValid(UUID userId, String jti);

    void revokeRefreshToken(UUID userId, String jti);

    void blacklistAccessToken(String jti, long remainingExpirationMs);

    boolean isAccessTokenBlacklisted(String jti);

    void revokeAllUserSessions(UUID userId);
}
