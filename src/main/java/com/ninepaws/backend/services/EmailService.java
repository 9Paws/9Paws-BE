package com.ninepaws.backend.services;

/** Service for sending transactional emails (such as OTP codes). */
public interface EmailService {

  /**
   * Sends registration OTP email to user.
   *
   * @param toEmail recipient email address
   * @param fullName recipient full name
   * @param otp 6-digit OTP code
   */
  void sendRegistrationOtp(String toEmail, String fullName, String otp);

  /**
   * Sends password reset OTP email to user.
   *
   * @param toEmail recipient email address
   * @param fullName recipient full name
   * @param otp 6-digit OTP code
   */
  void sendForgotPasswordOtp(String toEmail, String fullName, String otp);
}
