package com.ninepaws.backend.constants;

import com.ninepaws.backend.enums.OtpType;
import java.util.UUID;

public final class RedisKeyConstants {

  private static final String PREFIX = "9paws:";

  private RedisKeyConstants() {
    // Utility class
  }

  public static String otpKey(OtpType type, String email) {
    return PREFIX + "otp:" + type + ":" + email;
  }

  public static String otpCooldownKey(OtpType type, String email) {
    return PREFIX + "otp:cooldown:" + type + ":" + email;
  }

  public static String refreshTokenKey(UUID userId, String jti) {
    return PREFIX + "refresh_token:" + userId + ":" + jti;
  }

  public static String userSessionsKey(UUID userId) {
    return PREFIX + "user_sessions:" + userId;
  }

  public static String tokenBlacklistKey(String jti) {
    return PREFIX + "token:blacklist:" + jti;
  }

  public static String passwordResetTokenKey(String email) {
    return PREFIX + "password_reset_token:" + email;
  }
}
