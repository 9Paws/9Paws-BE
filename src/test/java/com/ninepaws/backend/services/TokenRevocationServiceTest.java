package com.ninepaws.backend.services;

import com.ninepaws.backend.services.impl.TokenRevocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private TokenRevocationServiceImpl tokenRevocationService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("recordRefreshToken stores token and adds jti to user sessions set")
    void recordRefreshToken_storesTokenAndAddsToUserSessionsSet() {
        UUID userId = UUID.randomUUID();
        tokenRevocationService.recordRefreshToken(userId, "jti-1", "token-value", 604800L);

        verify(valueOperations).set(eq("9paws:refresh_token:" + userId + ":jti-1"), eq("token-value"), eq(Duration.ofSeconds(604800L)));
        verify(setOperations).add(eq("9paws:user_sessions:" + userId), eq("jti-1"));
    }

    @Test
    @DisplayName("isRefreshTokenValid returns true when refresh token key exists in Redis")
    void isRefreshTokenValid_whenKeyExists_returnsTrue() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.hasKey("9paws:refresh_token:" + userId + ":jti-1")).thenReturn(true);

        assertThat(tokenRevocationService.isRefreshTokenValid(userId, "jti-1")).isTrue();
    }

    @Test
    @DisplayName("isRefreshTokenValid returns false when refresh token key does not exist")
    void isRefreshTokenValid_whenKeyDoesNotExist_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.hasKey("9paws:refresh_token:" + userId + ":jti-1")).thenReturn(false);

        assertThat(tokenRevocationService.isRefreshTokenValid(userId, "jti-1")).isFalse();
    }

    @Test
    @DisplayName("isRefreshTokenValid returns false when Redis returns null")
    void isRefreshTokenValid_whenRedisReturnsNull_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.hasKey("9paws:refresh_token:" + userId + ":jti-1")).thenReturn(null);

        assertThat(tokenRevocationService.isRefreshTokenValid(userId, "jti-1")).isFalse();
    }

    @Test
    @DisplayName("revokeRefreshToken deletes token and removes jti from user sessions set")
    void revokeRefreshToken_deletesTokenAndRemovesFromSessionSet() {
        UUID userId = UUID.randomUUID();
        tokenRevocationService.revokeRefreshToken(userId, "jti-1");

        verify(redisTemplate).delete("9paws:refresh_token:" + userId + ":jti-1");
        verify(setOperations).remove(eq("9paws:user_sessions:" + userId), eq("jti-1"));
    }

    @Test
    @DisplayName("blacklistAccessToken sets blacklist key with remaining TTL in milliseconds")
    void blacklistAccessToken_setsKeyWithRemainingTtl() {
        tokenRevocationService.blacklistAccessToken("jti-access-1", 15000L);

        verify(valueOperations).set(eq("9paws:token:blacklist:jti-access-1"), eq("REVOKED"), eq(Duration.ofMillis(15000L)));
    }

    @Test
    @DisplayName("blacklistAccessToken does not set key when remaining TTL is zero or negative")
    void blacklistAccessToken_whenTtlZeroOrNegative_doesNotSetKey() {
        tokenRevocationService.blacklistAccessToken("jti-zero", 0L);
        tokenRevocationService.blacklistAccessToken("jti-neg", -500L);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("isAccessTokenBlacklisted returns true when blacklist key exists in Redis")
    void isAccessTokenBlacklisted_whenKeyExists_returnsTrue() {
        when(redisTemplate.hasKey("9paws:token:blacklist:jti-access-1")).thenReturn(true);

        assertThat(tokenRevocationService.isAccessTokenBlacklisted("jti-access-1")).isTrue();
    }

    @Test
    @DisplayName("isAccessTokenBlacklisted returns false when key does not exist")
    void isAccessTokenBlacklisted_whenKeyDoesNotExist_returnsFalse() {
        when(redisTemplate.hasKey("9paws:token:blacklist:jti-access-1")).thenReturn(false);

        assertThat(tokenRevocationService.isAccessTokenBlacklisted("jti-access-1")).isFalse();
    }

    @Test
    @DisplayName("isAccessTokenBlacklisted returns false when Redis returns null")
    void isAccessTokenBlacklisted_whenRedisReturnsNull_returnsFalse() {
        when(redisTemplate.hasKey("9paws:token:blacklist:jti-access-1")).thenReturn(null);

        assertThat(tokenRevocationService.isAccessTokenBlacklisted("jti-access-1")).isFalse();
    }

    @Test
    @DisplayName("revokeAllUserSessions deletes all refresh token keys and session set")
    void revokeAllUserSessions_deletesAllRefreshKeysAndSessionSet() {
        UUID userId = UUID.randomUUID();
        when(setOperations.members("9paws:user_sessions:" + userId)).thenReturn(Set.of("jti-1", "jti-2"));

        tokenRevocationService.revokeAllUserSessions(userId);

        verify(redisTemplate).delete("9paws:refresh_token:" + userId + ":jti-1");
        verify(redisTemplate).delete("9paws:refresh_token:" + userId + ":jti-2");
        verify(redisTemplate).delete("9paws:user_sessions:" + userId);
    }

    @Test
    @DisplayName("revokeAllUserSessions deletes session set even if members set is empty")
    void revokeAllUserSessions_whenMembersEmpty_deletesSessionSetOnly() {
        UUID userId = UUID.randomUUID();
        when(setOperations.members("9paws:user_sessions:" + userId)).thenReturn(Collections.emptySet());

        tokenRevocationService.revokeAllUserSessions(userId);

        verify(redisTemplate).delete("9paws:user_sessions:" + userId);
    }

    @Test
    @DisplayName("revokeAllUserSessions deletes session set even if members returns null")
    void revokeAllUserSessions_whenMembersNull_deletesSessionSetOnly() {
        UUID userId = UUID.randomUUID();
        when(setOperations.members("9paws:user_sessions:" + userId)).thenReturn(null);

        tokenRevocationService.revokeAllUserSessions(userId);

        verify(redisTemplate).delete("9paws:user_sessions:" + userId);
    }
}
