package com.ninepaws.backend.services;

import com.ninepaws.backend.dtos.auth.AuthResponse;
import com.ninepaws.backend.dtos.auth.ChangePasswordRequest;
import com.ninepaws.backend.dtos.auth.ForgotPasswordRequest;
import com.ninepaws.backend.dtos.auth.LoginRequest;
import com.ninepaws.backend.dtos.auth.RefreshTokenRequest;
import com.ninepaws.backend.dtos.auth.RegisterRequest;
import com.ninepaws.backend.dtos.auth.ResendOtpRequest;
import com.ninepaws.backend.dtos.auth.ResetPasswordRequest;
import com.ninepaws.backend.dtos.auth.VerifyOtpRequest;
import com.ninepaws.backend.dtos.auth.VerifyResetPasswordOtpResponse;
import com.ninepaws.backend.dtos.auth.GoogleLoginRequest;
import java.util.UUID;

public interface AuthService {

  AuthResponse loginWithGoogle(GoogleLoginRequest request);

  void register(RegisterRequest request);

  AuthResponse verifyRegistrationOtp(VerifyOtpRequest request);

  void resendOtp(ResendOtpRequest request);

  AuthResponse login(LoginRequest request);

  AuthResponse refreshToken(RefreshTokenRequest request);

  void logout(String accessToken, String refreshToken);

  void logoutAll(UUID userId, String accessToken);

  void forgotPassword(ForgotPasswordRequest request);

  VerifyResetPasswordOtpResponse verifyForgotPasswordOtp(VerifyOtpRequest request);

  void resetPassword(ResetPasswordRequest request);

  void changePassword(UUID userId, String accessToken, ChangePasswordRequest request);
}
