package com.ninepaws.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.enums.OtpType;
import com.ninepaws.backend.exceptions.RateLimitException;
import com.ninepaws.backend.services.impl.OtpServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private OtpServiceImpl otpService;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("generateAndStoreOtp stores 6-digit number with 120s TTL and 60s cooldown")
  void generateAndStoreOtp_stores6DigitNumberWith120sTTL() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String otp = otpService.generateAndStoreOtp("user@example.com", OtpType.REGISTER);
    assertThat(otp).matches("^\\d{6}$");
    verify(valueOperations)
        .set(eq("9paws:otp:REGISTER:user@example.com"), eq(otp), eq(Duration.ofSeconds(120)));
    verify(valueOperations)
        .set(
            eq("9paws:otp:cooldown:REGISTER:user@example.com"),
            eq("SENT"),
            eq(Duration.ofSeconds(60)));
  }

  @Test
  @DisplayName("generateAndStoreOtp for FORGOT_PASSWORD stores with correct key and TTL")
  void generateAndStoreOtp_forForgotPassword_storesWithCorrectKey() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String otp = otpService.generateAndStoreOtp("user@example.com", OtpType.FORGOT_PASSWORD);
    assertThat(otp).matches("^\\d{6}$");
    verify(valueOperations)
        .set(
            eq("9paws:otp:FORGOT_PASSWORD:user@example.com"), eq(otp), eq(Duration.ofSeconds(120)));
    verify(valueOperations)
        .set(
            eq("9paws:otp:cooldown:FORGOT_PASSWORD:user@example.com"),
            eq("SENT"),
            eq(Duration.ofSeconds(60)));
  }

  @Test
  @DisplayName("checkCooldown when cooldown active throws RateLimitException")
  void checkCooldown_whenCooldownActive_throwsRateLimitException() {
    when(redisTemplate.hasKey("9paws:otp:cooldown:REGISTER:user@example.com")).thenReturn(true);
    assertThatThrownBy(() -> otpService.checkCooldown("user@example.com", OtpType.REGISTER))
        .isInstanceOf(RateLimitException.class);
  }

  @Test
  @DisplayName("checkCooldown when cooldown inactive completes normally")
  void checkCooldown_whenCooldownInactive_doesNotThrow() {
    when(redisTemplate.hasKey("9paws:otp:cooldown:REGISTER:user@example.com")).thenReturn(false);
    assertThatCode(() -> otpService.checkCooldown("user@example.com", OtpType.REGISTER))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("verifyOtp when valid deletes OTP from Redis and returns true")
  void verifyOtp_whenValid_deletesOtpAndReturnsTrue() {
    when(redisTemplate.opsForValue().get("9paws:otp:REGISTER:user@example.com"))
        .thenReturn("123456");
    boolean result = otpService.verifyOtp("user@example.com", OtpType.REGISTER, "123456");
    assertThat(result).isTrue();
    verify(redisTemplate).delete("9paws:otp:REGISTER:user@example.com");
  }

  @Test
  @DisplayName("verifyOtp when invalid returns false without deleting key")
  void verifyOtp_whenInvalid_returnsFalseWithoutDeletingKey() {
    when(redisTemplate.opsForValue().get("9paws:otp:REGISTER:user@example.com"))
        .thenReturn("123456");
    boolean result = otpService.verifyOtp("user@example.com", OtpType.REGISTER, "654321");
    assertThat(result).isFalse();
    verify(redisTemplate, never()).delete("9paws:otp:REGISTER:user@example.com");
  }

  @Test
  @DisplayName("verifyOtp when OTP expired or not in Redis returns false")
  void verifyOtp_whenOtpExpiredOrNotFound_returnsFalse() {
    when(redisTemplate.opsForValue().get("9paws:otp:REGISTER:user@example.com")).thenReturn(null);
    boolean result = otpService.verifyOtp("user@example.com", OtpType.REGISTER, "123456");
    assertThat(result).isFalse();
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  @DisplayName("verifyOtp when provided OTP is null returns false")
  void verifyOtp_whenInputOtpIsNull_returnsFalse() {
    boolean result = otpService.verifyOtp("user@example.com", OtpType.REGISTER, null);
    assertThat(result).isFalse();
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  @DisplayName("generateAndStorePasswordResetToken stores UUID token with 10-minute TTL")
  void generateAndStorePasswordResetToken_storesTokenWith10MinTTL() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String token = otpService.generateAndStorePasswordResetToken("user@example.com");
    assertThat(token).isNotBlank();
    assertThat(token).matches("^[0-9a-fA-F-]{36}$");
    verify(valueOperations)
        .set(
            eq("9paws:password_reset_token:user@example.com"),
            eq(token),
            eq(Duration.ofMinutes(10)));
  }

  @Test
  @DisplayName("verifyPasswordResetToken when valid deletes token from Redis and returns true")
  void verifyPasswordResetToken_whenValid_deletesTokenAndReturnsTrue() {
    when(redisTemplate.opsForValue().get("9paws:password_reset_token:user@example.com"))
        .thenReturn("valid-token-uuid");
    boolean result = otpService.verifyPasswordResetToken("user@example.com", "valid-token-uuid");
    assertThat(result).isTrue();
    verify(redisTemplate).delete("9paws:password_reset_token:user@example.com");
  }

  @Test
  @DisplayName("verifyPasswordResetToken when invalid returns false without deleting key")
  void verifyPasswordResetToken_whenInvalid_returnsFalseWithoutDeletingKey() {
    when(redisTemplate.opsForValue().get("9paws:password_reset_token:user@example.com"))
        .thenReturn("valid-token-uuid");
    boolean result = otpService.verifyPasswordResetToken("user@example.com", "wrong-token-uuid");
    assertThat(result).isFalse();
    verify(redisTemplate, never()).delete("9paws:password_reset_token:user@example.com");
  }

  @Test
  @DisplayName("verifyPasswordResetToken when token expired or not found returns false")
  void verifyPasswordResetToken_whenExpiredOrNotFound_returnsFalse() {
    when(redisTemplate.opsForValue().get("9paws:password_reset_token:user@example.com"))
        .thenReturn(null);
    boolean result = otpService.verifyPasswordResetToken("user@example.com", "any-token");
    assertThat(result).isFalse();
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  @DisplayName("verifyPasswordResetToken when provided token is null returns false")
  void verifyPasswordResetToken_whenTokenIsNull_returnsFalse() {
    boolean result = otpService.verifyPasswordResetToken("user@example.com", null);
    assertThat(result).isFalse();
    verify(redisTemplate, never()).delete(anyString());
  }
}
