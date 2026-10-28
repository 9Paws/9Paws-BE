package com.ninepaws.backend.controllers;

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
import com.ninepaws.backend.dtos.response.base.SingleResponse;
import com.ninepaws.backend.dtos.response.base.SuccessResponse;
import com.ninepaws.backend.security.SecurityUtils;
import com.ninepaws.backend.services.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends AbstractBaseController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<SuccessResponse> register(@Valid @RequestBody RegisterRequest request) {
    authService.register(request);
    return created("User registered successfully. Please verify your email with the OTP sent.");
  }

  @PostMapping("/verify-registration-otp")
  public ResponseEntity<SingleResponse<AuthResponse>> verifyRegistrationOtp(
      @Valid @RequestBody VerifyOtpRequest request) {
    AuthResponse response = authService.verifyRegistrationOtp(request);
    return success(response, "Email verified successfully");
  }

  @PostMapping("/resend-otp")
  public ResponseEntity<SuccessResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
    authService.resendOtp(request);
    return success("OTP sent successfully");
  }

  @PostMapping("/login")
  public ResponseEntity<SingleResponse<AuthResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return success(response, "Login successful");
  }

  @PostMapping("/google")
  public ResponseEntity<SingleResponse<AuthResponse>> loginWithGoogle(
      @Valid @RequestBody GoogleLoginRequest request) {
    AuthResponse response = authService.loginWithGoogle(request);
    return success(response, "Đăng nhập Google thành công");
  }

  @PostMapping("/refresh-token")
  public ResponseEntity<SingleResponse<AuthResponse>> refreshToken(
      @Valid @RequestBody RefreshTokenRequest request) {
    AuthResponse response = authService.refreshToken(request);
    return success(response, "Token refreshed successfully");
  }

  @PostMapping("/logout")
  public ResponseEntity<SuccessResponse> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @Valid @RequestBody(required = false) RefreshTokenRequest request) {
    String refreshToken = request != null ? request.refreshToken() : null;
    authService.logout(authHeader, refreshToken);
    return success("Logged out successfully");
  }

  @PostMapping("/logout-all")
  public ResponseEntity<SuccessResponse> logoutAll(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    UUID userId = SecurityUtils.getCurrentUserId();
    authService.logoutAll(userId, authHeader);
    return success("Logged out from all devices successfully");
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<SuccessResponse> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return success("Password reset OTP sent to email if account exists");
  }

  @PostMapping("/verify-forgot-password-otp")
  public ResponseEntity<SingleResponse<VerifyResetPasswordOtpResponse>> verifyForgotPasswordOtp(
      @Valid @RequestBody VerifyOtpRequest request) {
    VerifyResetPasswordOtpResponse response = authService.verifyForgotPasswordOtp(request);
    return success(response, "OTP verified successfully");
  }

  @PostMapping("/reset-password")
  public ResponseEntity<SuccessResponse> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return success("Password reset successfully. Please login with your new password.");
  }

  @PostMapping("/change-password")
  public ResponseEntity<SuccessResponse> changePassword(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @Valid @RequestBody ChangePasswordRequest request) {
    UUID userId = SecurityUtils.getCurrentUserId();
    authService.changePassword(userId, authHeader, request);
    return success("Password changed successfully");
  }
}
