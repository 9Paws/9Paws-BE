package com.ninepaws.backend.services.impl;

import com.ninepaws.backend.dtos.auth.AuthResponse;
import com.ninepaws.backend.dtos.auth.ChangePasswordRequest;
import com.ninepaws.backend.dtos.auth.ForgotPasswordRequest;
import com.ninepaws.backend.dtos.auth.LoginRequest;
import com.ninepaws.backend.dtos.auth.RefreshTokenRequest;
import com.ninepaws.backend.dtos.auth.RegisterRequest;
import com.ninepaws.backend.dtos.auth.ResendOtpRequest;
import com.ninepaws.backend.dtos.auth.ResetPasswordRequest;
import com.ninepaws.backend.dtos.auth.UserResponse;
import com.ninepaws.backend.dtos.auth.VerifyOtpRequest;
import com.ninepaws.backend.dtos.auth.VerifyResetPasswordOtpResponse;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.enums.OtpType;
import com.ninepaws.backend.exceptions.AccountNotVerifiedException;
import com.ninepaws.backend.exceptions.DuplicateResourceException;
import com.ninepaws.backend.exceptions.InvalidOtpException;
import com.ninepaws.backend.exceptions.InvalidPasswordException;
import com.ninepaws.backend.exceptions.ResourceNotFoundException;
import com.ninepaws.backend.exceptions.TokenRevokedException;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.JwtService;
import com.ninepaws.backend.security.TokenPayload;
import com.ninepaws.backend.services.AuthService;
import com.ninepaws.backend.services.EmailService;
import com.ninepaws.backend.services.OtpService;
import com.ninepaws.backend.services.TokenRevocationService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private static final String BEARER_PREFIX = "Bearer ";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final OtpService otpService;
  private final EmailService emailService;
  private final TokenRevocationService tokenRevocationService;

  @Override
  @Transactional
  public void register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new DuplicateResourceException("Email already exists: " + request.email());
    }

    User user =
        User.builder()
            .fullName(request.fullName())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .authProvider(AuthProvider.LOCAL)
            .emailVerified(false)
            .biometricEnabled(false)
            .dailyReminderEnabled(true)
            .currency("VND")
            .language("vi")
            .build();

    userRepository.save(user);

    String otp = otpService.generateAndStoreOtp(request.email(), OtpType.REGISTER);
    emailService.sendRegistrationOtp(request.email(), request.fullName(), otp);
    log.info("Registered unverified user and dispatched OTP to email: {}", request.email());
  }

  @Override
  @Transactional
  public AuthResponse verifyRegistrationOtp(VerifyOtpRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("User not found with email: " + request.email()));

    boolean valid = otpService.verifyOtp(request.email(), OtpType.REGISTER, request.otp());
    if (!valid) {
      throw new InvalidOtpException("Invalid or expired OTP");
    }

    user.setEmailVerified(true);
    userRepository.save(user);
    log.info("Verified registration email for user: {}", user.getEmail());

    return generateAuthResponse(user);
  }

  @Override
  @Transactional(readOnly = true)
  public void resendOtp(ResendOtpRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("User not found with email: " + request.email()));

    otpService.checkCooldown(request.email(), request.type());
    String otp = otpService.generateAndStoreOtp(request.email(), request.type());

    if (request.type() == OtpType.REGISTER) {
      emailService.sendRegistrationOtp(user.getEmail(), user.getFullName(), otp);
    } else if (request.type() == OtpType.FORGOT_PASSWORD) {
      emailService.sendForgotPasswordOtp(user.getEmail(), user.getFullName(), otp);
    }

    log.info("Resent OTP of type {} to email: {}", request.type(), request.email());
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid email or password");
    }

    if (!user.isEmailVerified()) {
      throw new AccountNotVerifiedException(
          "Account is not verified. Please verify your email via OTP before logging in.");
    }

    log.info("User logged in successfully: {}", user.getEmail());
    return generateAuthResponse(user);
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResponse refreshToken(RefreshTokenRequest request) {
    TokenPayload payload = jwtService.parseAndValidateToken(request.refreshToken());
    if (payload == null || !"REFRESH".equalsIgnoreCase(payload.tokenType())) {
      throw new TokenRevokedException("Invalid token type");
    }

    if (!tokenRevocationService.isRefreshTokenValid(payload.userId(), payload.jti())) {
      throw new TokenRevokedException("Refresh token is revoked or invalid");
    }

    User user =
        userRepository
            .findById(payload.userId())
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found with id: " + payload.userId()));

    tokenRevocationService.revokeRefreshToken(payload.userId(), payload.jti());
    log.info("Rotated refresh token for user: {}", payload.userId());

    return generateAuthResponse(user, payload.userId());
  }

  @Override
  @Transactional
  public void logout(String accessToken, String refreshToken) {
    if (accessToken != null && !accessToken.isBlank()) {
      String cleanToken =
          accessToken.startsWith(BEARER_PREFIX)
              ? accessToken.substring(BEARER_PREFIX.length())
              : accessToken;
      try {
        TokenPayload accessPayload = jwtService.parseAndValidateToken(cleanToken);
        if (accessPayload != null && accessPayload.jti() != null) {
          long remainingMs =
              accessPayload.expiresAt() != null
                  ? Math.max(
                      0L, accessPayload.expiresAt().toEpochMilli() - System.currentTimeMillis())
                  : 0L;
          tokenRevocationService.blacklistAccessToken(accessPayload.jti(), remainingMs);
        }
      } catch (Exception e) {
        log.warn("Failed to blacklist access token during logout: {}", e.getMessage());
      }
    }

    if (refreshToken != null && !refreshToken.isBlank()) {
      try {
        TokenPayload refreshPayload = jwtService.parseAndValidateToken(refreshToken);
        if (refreshPayload != null
            && refreshPayload.userId() != null
            && refreshPayload.jti() != null) {
          tokenRevocationService.revokeRefreshToken(refreshPayload.userId(), refreshPayload.jti());
        }
      } catch (Exception e) {
        log.warn("Failed to revoke refresh token during logout: {}", e.getMessage());
      }
    }
    log.info("Completed logout processing");
  }

  @Override
  @Transactional
  public void logoutAll(UUID userId, String accessToken) {
    if (accessToken != null && !accessToken.isBlank()) {
      String cleanToken =
          accessToken.startsWith(BEARER_PREFIX)
              ? accessToken.substring(BEARER_PREFIX.length())
              : accessToken;
      try {
        TokenPayload accessPayload = jwtService.parseAndValidateToken(cleanToken);
        if (accessPayload != null && accessPayload.jti() != null) {
          long remainingMs =
              accessPayload.expiresAt() != null
                  ? Math.max(
                      0L, accessPayload.expiresAt().toEpochMilli() - System.currentTimeMillis())
                  : 0L;
          tokenRevocationService.blacklistAccessToken(accessPayload.jti(), remainingMs);
        }
      } catch (Exception e) {
        log.warn("Failed to blacklist access token during logoutAll: {}", e.getMessage());
      }
    }

    tokenRevocationService.revokeAllUserSessions(userId);
    log.info("Revoked all sessions for user: {}", userId);
  }

  @Override
  @Transactional(readOnly = true)
  public void forgotPassword(ForgotPasswordRequest request) {
    Optional<User> userOpt = userRepository.findByEmail(request.email());
    if (userOpt.isEmpty()) {
      log.info("Forgot password requested for non-existent email: {}", request.email());
      return;
    }

    User user = userOpt.get();
    otpService.checkCooldown(user.getEmail(), OtpType.FORGOT_PASSWORD);
    String otp = otpService.generateAndStoreOtp(user.getEmail(), OtpType.FORGOT_PASSWORD);
    emailService.sendForgotPasswordOtp(user.getEmail(), user.getFullName(), otp);
    log.info("Dispatched forgot password OTP for email: {}", user.getEmail());
  }

  @Override
  @Transactional(readOnly = true)
  public VerifyResetPasswordOtpResponse verifyForgotPasswordOtp(VerifyOtpRequest request) {
    if (!userRepository.existsByEmail(request.email())) {
      throw new ResourceNotFoundException("User not found with email: " + request.email());
    }

    boolean valid = otpService.verifyOtp(request.email(), OtpType.FORGOT_PASSWORD, request.otp());
    if (!valid) {
      throw new InvalidOtpException("Invalid or expired OTP");
    }

    String resetToken = otpService.generateAndStorePasswordResetToken(request.email());
    log.info("Verified forgot password OTP and generated reset token for: {}", request.email());
    return new VerifyResetPasswordOtpResponse(request.email(), resetToken);
  }

  @Override
  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    boolean validToken = otpService.verifyPasswordResetToken(request.email(), request.resetToken());
    if (!validToken) {
      throw new InvalidOtpException("Invalid or expired password reset token");
    }

    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("User not found with email: " + request.email()));

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);

    tokenRevocationService.revokeAllUserSessions(user.getId());
    log.info("Reset password and revoked all sessions for user: {}", user.getEmail());
  }

  @Override
  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    if (!request.newPassword().equals(request.confirmPassword())) {
      throw new InvalidPasswordException("New password and confirmation password do not match");
    }

    if (request.newPassword().equals(request.currentPassword())) {
      throw new InvalidPasswordException("New password must be different from current password");
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new InvalidPasswordException("Current password is incorrect");
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);

    tokenRevocationService.revokeAllUserSessions(userId);
    log.info("Changed password and revoked all sessions for user: {}", userId);
  }

  private AuthResponse generateAuthResponse(User user) {
    return generateAuthResponse(user, user.getId());
  }

  private AuthResponse generateAuthResponse(User user, UUID fallbackUserId) {
    UUID effectiveUserId = user.getId() != null ? user.getId() : fallbackUserId;
    if (user.getId() == null && effectiveUserId != null) {
      user.setId(effectiveUserId);
    }

    String accessJti = UUID.randomUUID().toString();
    String refreshJti = UUID.randomUUID().toString();

    String accessToken =
        jwtService.generateAccessToken(effectiveUserId, user.getEmail(), accessJti);
    String refreshToken = jwtService.generateRefreshToken(effectiveUserId, refreshJti);

    tokenRevocationService.recordRefreshToken(
        effectiveUserId, refreshJti, refreshToken, jwtService.getRefreshTokenExpirationSeconds());

    UserResponse userResponse =
        new UserResponse(
            effectiveUserId,
            user.getFullName(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getAvatarUrl(),
            user.isEmailVerified(),
            user.getCurrency(),
            user.getLanguage());

    return new AuthResponse(
        accessToken,
        refreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        userResponse);
  }
}
