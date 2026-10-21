package com.ninepaws.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  // 32-byte secret (256 bits) encoded in Base64
  private static final String VALID_BASE64_SECRET = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";
  private static final long ACCESS_TOKEN_EXPIRATION_MS = 1800000L; // 30 min
  private static final long REFRESH_TOKEN_EXPIRATION_MS = 604800000L; // 7 days

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    jwtService =
        new JwtService(
            VALID_BASE64_SECRET, ACCESS_TOKEN_EXPIRATION_MS, REFRESH_TOKEN_EXPIRATION_MS);
  }

  @Test
  @DisplayName("generateAccessToken and parseAndValidateToken returns correct claims")
  void generateAndParseAccessToken_returnsCorrectClaims() {
    UUID userId = UUID.randomUUID();
    String jti = UUID.randomUUID().toString();
    String token = jwtService.generateAccessToken(userId, "user@example.com", jti);

    TokenPayload payload = jwtService.parseAndValidateToken(token);
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.email()).isEqualTo("user@example.com");
    assertThat(payload.jti()).isEqualTo(jti);
    assertThat(payload.tokenType()).isEqualTo("ACCESS");
    assertThat(payload.issuedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    assertThat(payload.expiresAt()).isNotNull().isAfter(payload.issuedAt());
  }

  @Test
  @DisplayName("generateRefreshToken and parseAndValidateToken returns correct claims")
  void generateAndParseRefreshToken_returnsCorrectClaims() {
    UUID userId = UUID.randomUUID();
    String jti = UUID.randomUUID().toString();
    String token = jwtService.generateRefreshToken(userId, jti);

    TokenPayload payload = jwtService.parseAndValidateToken(token);
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.jti()).isEqualTo(jti);
    assertThat(payload.tokenType()).isEqualTo("REFRESH");
    assertThat(payload.email()).isNull();
    assertThat(payload.issuedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    assertThat(payload.expiresAt()).isNotNull().isAfter(payload.issuedAt());
  }

  @Test
  @DisplayName("whenTokenTampered_parseThrowsJwtException")
  void whenTokenTampered_parseThrowsJwtException() {
    String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@example.com", "jti-1");
    String tampered = token + "xyz";
    assertThatThrownBy(() -> jwtService.parseAndValidateToken(tampered))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("when token is expired, parseAndValidateToken throws JwtException")
  void whenTokenExpired_parseThrowsJwtException() {
    JwtService shortLivedJwtService = new JwtService(VALID_BASE64_SECRET, -1000L, -1000L);
    String token =
        shortLivedJwtService.generateAccessToken(
            UUID.randomUUID(), "test@example.com", "jti-expired");

    assertThatThrownBy(() -> jwtService.parseAndValidateToken(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("when secret is too short (< 256 bits), constructor throws IllegalArgumentException")
  void whenSecretTooShort_constructorThrowsIllegalArgumentException() {
    // 16 bytes base64 encoded -> 128 bits
    String shortSecret = Base64.getEncoder().encodeToString("1234567890123456".getBytes());
    assertThatThrownBy(
            () ->
                new JwtService(
                    shortSecret, ACCESS_TOKEN_EXPIRATION_MS, REFRESH_TOKEN_EXPIRATION_MS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 256 bits");
  }

  @Test
  @DisplayName("when secret is null or blank, constructor throws IllegalArgumentException")
  void whenSecretNullOrBlank_constructorThrowsIllegalArgumentException() {
    assertThatThrownBy(
            () -> new JwtService(null, ACCESS_TOKEN_EXPIRATION_MS, REFRESH_TOKEN_EXPIRATION_MS))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () -> new JwtService("   ", ACCESS_TOKEN_EXPIRATION_MS, REFRESH_TOKEN_EXPIRATION_MS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("getRemainingExpirationMs returns positive value for valid token")
  void getRemainingExpirationMs_forValidToken_returnsPositiveValue() {
    String token =
        jwtService.generateAccessToken(UUID.randomUUID(), "test@example.com", "jti-remaining");
    long remaining = jwtService.getRemainingExpirationMs(token);

    assertThat(remaining).isPositive();
    assertThat(remaining).isLessThanOrEqualTo(ACCESS_TOKEN_EXPIRATION_MS);
  }

  @Test
  @DisplayName("getRemainingExpirationMs returns zero for expired token")
  void getRemainingExpirationMs_forExpiredToken_returnsZero() {
    JwtService shortLivedJwtService = new JwtService(VALID_BASE64_SECRET, -5000L, -5000L);
    String expiredToken =
        shortLivedJwtService.generateAccessToken(UUID.randomUUID(), "test@example.com", "jti-exp");

    long remaining = jwtService.getRemainingExpirationMs(expiredToken);
    assertThat(remaining).isZero();
  }

  @Test
  @DisplayName("getRemainingExpirationMs for tampered token throws JwtException")
  void getRemainingExpirationMs_forTamperedToken_throwsJwtException() {
    String token =
        jwtService.generateAccessToken(UUID.randomUUID(), "test@example.com", "jti-valid");
    String tampered = token + "xyz";

    assertThatThrownBy(() -> jwtService.getRemainingExpirationMs(tampered))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("getAccessTokenExpirationSeconds returns correct seconds")
  void getAccessTokenExpirationSeconds_returnsCorrectSeconds() {
    assertThat(jwtService.getAccessTokenExpirationSeconds())
        .isEqualTo(ACCESS_TOKEN_EXPIRATION_MS / 1000);
  }

  @Test
  @DisplayName("getRefreshTokenExpirationSeconds returns correct seconds")
  void getRefreshTokenExpirationSeconds_returnsCorrectSeconds() {
    assertThat(jwtService.getRefreshTokenExpirationSeconds())
        .isEqualTo(REFRESH_TOKEN_EXPIRATION_MS / 1000);
  }
}
