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
import com.ninepaws.backend.dtos.common.ApiResponse;
import com.ninepaws.backend.security.SecurityUtils;
import com.ninepaws.backend.services.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
    authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "User registered successfully. Please verify your email with the OTP sent.", null));
  }

  @PostMapping("/verify-registration-otp")
  public ResponseEntity<ApiResponse<AuthResponse>> verifyRegistrationOtp(
      @Valid @RequestBody VerifyOtpRequest request) {
    AuthResponse response = authService.verifyRegistrationOtp(request);
    return ResponseEntity.ok(ApiResponse.success("Email verified successfully", response));
  }

  @PostMapping("/resend-otp")
  public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
    authService.resendOtp(request);
    return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", null));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success("Login successful", response));
  }

  @PostMapping("/refresh-token")
  public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
      @Valid @RequestBody RefreshTokenRequest request) {
    AuthResponse response = authService.refreshToken(request);
    return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @Valid @RequestBody(required = false) RefreshTokenRequest request) {
    String refreshToken = request != null ? request.refreshToken() : null;
    authService.logout(authHeader, refreshToken);
    return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
  }

  @PostMapping("/logout-all")
  public ResponseEntity<ApiResponse<Void>> logoutAll(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    UUID userId = SecurityUtils.getCurrentUserId();
    authService.logoutAll(userId, authHeader);
    return ResponseEntity.ok(ApiResponse.success("Logged out from all devices successfully", null));
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ResponseEntity.ok(
        ApiResponse.success("Password reset OTP sent to email if account exists", null));
  }

  @PostMapping("/verify-forgot-password-otp")
  public ResponseEntity<ApiResponse<VerifyResetPasswordOtpResponse>> verifyForgotPasswordOtp(
      @Valid @RequestBody VerifyOtpRequest request) {
    VerifyResetPasswordOtpResponse response = authService.verifyForgotPasswordOtp(request);
    return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(
        ApiResponse.success(
            "Password reset successfully. Please login with your new password.", null));
  }

  @PostMapping("/change-password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    UUID userId = SecurityUtils.getCurrentUserId();
    authService.changePassword(userId, request);
    return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
  }
}
