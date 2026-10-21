package com.ninepaws.backend.services;

import com.ninepaws.backend.enums.OtpType;

public interface OtpService {

  String generateAndStoreOtp(String email, OtpType type);

  boolean verifyOtp(String email, OtpType type, String otp);

  void checkCooldown(String email, OtpType type);

  String generateAndStorePasswordResetToken(String email);

  boolean verifyPasswordResetToken(String email, String token);
}
