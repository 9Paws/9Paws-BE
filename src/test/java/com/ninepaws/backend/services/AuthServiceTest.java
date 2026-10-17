package com.ninepaws.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.enums.OtpType;
import com.ninepaws.backend.exceptions.AccountNotVerifiedException;
import com.ninepaws.backend.exceptions.DuplicateResourceException;
import com.ninepaws.backend.exceptions.InvalidOtpException;
import com.ninepaws.backend.exceptions.InvalidPasswordException;
import com.ninepaws.backend.exceptions.RateLimitException;
import com.ninepaws.backend.exceptions.ResourceNotFoundException;
import com.ninepaws.backend.exceptions.TokenRevokedException;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.JwtService;
import com.ninepaws.backend.security.TokenPayload;
import com.ninepaws.backend.services.impl.AuthServiceImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private OtpService otpService;
  @Mock private EmailService emailService;
  @Mock private TokenRevocationService tokenRevocationService;

  @InjectMocks private AuthServiceImpl authService;

  private final String testEmail = "user@example.com";
  private final String testPassword = "Password123@";
  private final String encodedPassword = "encoded-password-hash";

  @Nested
  @DisplayName("register")
  class RegisterTests {

    @Test
    @DisplayName("register when email already exists throws DuplicateResourceException")
    void register_whenEmailAlreadyExists_throwsDuplicateResourceException() {
      when(userRepository.existsByEmail(testEmail)).thenReturn(true);
      RegisterRequest request = new RegisterRequest("User", testEmail, testPassword);

      assertThatThrownBy(() -> authService.register(request))
          .isInstanceOf(DuplicateResourceException.class);
      verify(userRepository, never()).save(any(User.class));
      verify(emailService, never()).sendRegistrationOtp(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("register when email is new saves unverified user, generates OTP, and sends email")
    void register_whenEmailIsNew_savesUserAndGeneratesOtpAndSendsEmail() {
      when(userRepository.existsByEmail(testEmail)).thenReturn(false);
      when(passwordEncoder.encode(testPassword)).thenReturn(encodedPassword);
      when(otpService.generateAndStoreOtp(testEmail, OtpType.REGISTER)).thenReturn("123456");

      RegisterRequest request = new RegisterRequest("Nguyen Van A", testEmail, testPassword);
      authService.register(request);

      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(userCaptor.capture());
      User savedUser = userCaptor.getValue();
      assertThat(savedUser.getEmail()).isEqualTo(testEmail);
      assertThat(savedUser.getFullName()).isEqualTo("Nguyen Van A");
      assertThat(savedUser.getPasswordHash()).isEqualTo(encodedPassword);
      assertThat(savedUser.isEmailVerified()).isFalse();
      assertThat(savedUser.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);

      verify(otpService).generateAndStoreOtp(testEmail, OtpType.REGISTER);
      verify(emailService).sendRegistrationOtp(testEmail, "Nguyen Van A", "123456");
    }
  }

  @Nested
  @DisplayName("verifyRegistrationOtp")
  class VerifyRegistrationOtpTests {

    @Test
    @DisplayName("verifyRegistrationOtp when user not found throws ResourceNotFoundException")
    void verifyRegistrationOtp_whenUserNotFound_throwsResourceNotFoundException() {
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
      VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "123456");

      assertThatThrownBy(() -> authService.verifyRegistrationOtp(request))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("verifyRegistrationOtp when OTP invalid throws InvalidOtpException")
    void verifyRegistrationOtp_whenOtpInvalid_throwsInvalidOtpException() {
      User user = User.builder().email(testEmail).fullName("User").emailVerified(false).build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(otpService.verifyOtp(testEmail, OtpType.REGISTER, "000000")).thenReturn(false);

      VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "000000");
      assertThatThrownBy(() -> authService.verifyRegistrationOtp(request))
          .isInstanceOf(InvalidOtpException.class);
      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("verifyRegistrationOtp when OTP valid updates emailVerified and issues tokens")
    void verifyRegistrationOtp_whenOtpValid_verifiesUserAndIssuesTokens() {
      UUID userId = UUID.randomUUID();
      User user = User.builder().id(userId).email(testEmail).fullName("Nguyen Van A").emailVerified(false).build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(otpService.verifyOtp(testEmail, OtpType.REGISTER, "123456")).thenReturn(true);
      when(jwtService.generateAccessToken(eq(userId), eq(testEmail), anyString())).thenReturn("access-token");
      when(jwtService.generateRefreshToken(eq(userId), anyString())).thenReturn("refresh-token");
      when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(1800L);
      when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

      VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "123456");
      AuthResponse response = authService.verifyRegistrationOtp(request);

      assertThat(user.isEmailVerified()).isTrue();
      verify(userRepository).save(user);
      verify(tokenRevocationService).recordRefreshToken(eq(userId), anyString(), eq("refresh-token"), eq(604800L));
      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(1800L);
      assertThat(response.user().email()).isEqualTo(testEmail);
      assertThat(response.user().emailVerified()).isTrue();
    }
  }

  @Nested
  @DisplayName("resendOtp")
  class ResendOtpTests {

    @Test
    @DisplayName("resendOtp when user not found throws ResourceNotFoundException")
    void resendOtp_whenUserNotFound_throwsResourceNotFoundException() {
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
      ResendOtpRequest request = new ResendOtpRequest(testEmail, OtpType.REGISTER);

      assertThatThrownBy(() -> authService.resendOtp(request))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("resendOtp when cooldown active throws RateLimitException")
    void resendOtp_whenCooldownActive_throwsRateLimitException() {
      User user = User.builder().email(testEmail).fullName("User").build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      doThrow(new RateLimitException("OTP cooldown active"))
          .when(otpService).checkCooldown(testEmail, OtpType.REGISTER);

      ResendOtpRequest request = new ResendOtpRequest(testEmail, OtpType.REGISTER);
      assertThatThrownBy(() -> authService.resendOtp(request))
          .isInstanceOf(RateLimitException.class);
      verify(otpService, never()).generateAndStoreOtp(anyString(), any(OtpType.class));
      verify(emailService, never()).sendRegistrationOtp(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resendOtp for REGISTER sends registration email")
    void resendOtp_whenRegisterType_generatesOtpAndSendsRegistrationEmail() {
      User user = User.builder().email(testEmail).fullName("Nguyen Van A").build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(otpService.generateAndStoreOtp(testEmail, OtpType.REGISTER)).thenReturn("654321");

      ResendOtpRequest request = new ResendOtpRequest(testEmail, OtpType.REGISTER);
      authService.resendOtp(request);

      verify(otpService).checkCooldown(testEmail, OtpType.REGISTER);
      verify(otpService).generateAndStoreOtp(testEmail, OtpType.REGISTER);
      verify(emailService).sendRegistrationOtp(testEmail, "Nguyen Van A", "654321");
    }

    @Test
    @DisplayName("resendOtp for FORGOT_PASSWORD sends forgot password email")
    void resendOtp_whenForgotPasswordType_generatesOtpAndSendsForgotPasswordEmail() {
      User user = User.builder().email(testEmail).fullName("Nguyen Van A").build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(otpService.generateAndStoreOtp(testEmail, OtpType.FORGOT_PASSWORD)).thenReturn("789012");

      ResendOtpRequest request = new ResendOtpRequest(testEmail, OtpType.FORGOT_PASSWORD);
      authService.resendOtp(request);

      verify(otpService).checkCooldown(testEmail, OtpType.FORGOT_PASSWORD);
      verify(otpService).generateAndStoreOtp(testEmail, OtpType.FORGOT_PASSWORD);
      verify(emailService).sendForgotPasswordOtp(testEmail, "Nguyen Van A", "789012");
    }
  }

  @Nested
  @DisplayName("login")
  class LoginTests {

    @Test
    @DisplayName("login when user not verified throws AccountNotVerifiedException")
    void login_whenUserNotVerified_throwsAccountNotVerifiedException() {
      User user = User.builder().email(testEmail).passwordHash("hash").emailVerified(false).build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(passwordEncoder.matches(testPassword, "hash")).thenReturn(true);

      LoginRequest request = new LoginRequest(testEmail, testPassword);
      assertThatThrownBy(() -> authService.login(request))
          .isInstanceOf(AccountNotVerifiedException.class);
    }

    @Test
    @DisplayName("login when user not found throws BadCredentialsException")
    void login_whenUserNotFound_throwsBadCredentialsException() {
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

      LoginRequest request = new LoginRequest(testEmail, testPassword);
      assertThatThrownBy(() -> authService.login(request))
          .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login when password incorrect throws BadCredentialsException")
    void login_whenPasswordIncorrect_throwsBadCredentialsException() {
      User user = User.builder().email(testEmail).passwordHash("hash").emailVerified(true).build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(passwordEncoder.matches("WrongPass", "hash")).thenReturn(false);

      LoginRequest request = new LoginRequest(testEmail, "WrongPass");
      assertThatThrownBy(() -> authService.login(request))
          .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login when credentials valid and email verified returns AuthResponse")
    void login_whenCredentialsValidAndVerified_returnsAuthResponse() {
      UUID userId = UUID.randomUUID();
      User user = User.builder().id(userId).email(testEmail).fullName("Nguyen Van A").passwordHash("hash").emailVerified(true).build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(passwordEncoder.matches(testPassword, "hash")).thenReturn(true);
      when(jwtService.generateAccessToken(eq(userId), eq(testEmail), anyString())).thenReturn("login-access");
      when(jwtService.generateRefreshToken(eq(userId), anyString())).thenReturn("login-refresh");
      when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(1800L);
      when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

      LoginRequest request = new LoginRequest(testEmail, testPassword);
      AuthResponse response = authService.login(request);

      assertThat(response.accessToken()).isEqualTo("login-access");
      assertThat(response.refreshToken()).isEqualTo("login-refresh");
      assertThat(response.user().email()).isEqualTo(testEmail);
      verify(tokenRevocationService).recordRefreshToken(eq(userId), anyString(), eq("login-refresh"), eq(604800L));
    }
  }

  @Nested
  @DisplayName("refreshToken")
  class RefreshTokenTests {

    @Test
    @DisplayName("refreshToken when token valid rotates and issues new tokens")
    void refreshToken_whenTokenValid_rotatesAndIssuesNewTokens() {
      UUID userId = UUID.randomUUID();
      TokenPayload oldPayload = new TokenPayload(userId, testEmail, "jti-old", "REFRESH", Instant.now(), Instant.now().plusSeconds(604800));
      when(jwtService.parseAndValidateToken("old-refresh-token")).thenReturn(oldPayload);
      when(tokenRevocationService.isRefreshTokenValid(userId, "jti-old")).thenReturn(true);
      when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().email(testEmail).fullName("Test").build()));

      when(jwtService.generateAccessToken(eq(userId), eq(testEmail), anyString())).thenReturn("new-access");
      when(jwtService.generateRefreshToken(eq(userId), anyString())).thenReturn("new-refresh");

      AuthResponse res = authService.refreshToken(new RefreshTokenRequest("old-refresh-token"));

      assertThat(res.accessToken()).isEqualTo("new-access");
      assertThat(res.refreshToken()).isEqualTo("new-refresh");
      verify(tokenRevocationService).revokeRefreshToken(userId, "jti-old");
      verify(tokenRevocationService).recordRefreshToken(eq(userId), anyString(), eq("new-refresh"), anyLong());
    }

    @Test
    @DisplayName("refreshToken when token type is not REFRESH throws TokenRevokedException")
    void refreshToken_whenTokenTypeNotRefresh_throwsTokenRevokedException() {
      UUID userId = UUID.randomUUID();
      TokenPayload accessPayload = new TokenPayload(userId, testEmail, "jti-1", "ACCESS", Instant.now(), Instant.now().plusSeconds(1800));
      when(jwtService.parseAndValidateToken("access-token-wrong")).thenReturn(accessPayload);

      assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("access-token-wrong")))
          .isInstanceOf(TokenRevokedException.class);
      verify(tokenRevocationService, never()).revokeRefreshToken(any(), any());
    }

    @Test
    @DisplayName("refreshToken when refresh token is revoked or absent in Redis throws TokenRevokedException")
    void refreshToken_whenTokenNotValidInRedis_throwsTokenRevokedException() {
      UUID userId = UUID.randomUUID();
      TokenPayload payload = new TokenPayload(userId, testEmail, "jti-revoked", "REFRESH", Instant.now(), Instant.now().plusSeconds(604800));
      when(jwtService.parseAndValidateToken("revoked-token")).thenReturn(payload);
      when(tokenRevocationService.isRefreshTokenValid(userId, "jti-revoked")).thenReturn(false);

      assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("revoked-token")))
          .isInstanceOf(TokenRevokedException.class);
      verify(tokenRevocationService, never()).revokeRefreshToken(any(), any());
    }

    @Test
    @DisplayName("refreshToken when user not found throws ResourceNotFoundException")
    void refreshToken_whenUserNotFound_throwsResourceNotFoundException() {
      UUID userId = UUID.randomUUID();
      TokenPayload payload = new TokenPayload(userId, testEmail, "jti-valid", "REFRESH", Instant.now(), Instant.now().plusSeconds(604800));
      when(jwtService.parseAndValidateToken("valid-token")).thenReturn(payload);
      when(tokenRevocationService.isRefreshTokenValid(userId, "jti-valid")).thenReturn(true);
      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("valid-token")))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("logout")
  class LogoutTests {

    @Test
    @DisplayName("logout when valid access and refresh tokens blacklists access and revokes refresh")
    void logout_whenValidTokens_blacklistsAccessAndRevokesRefresh() {
      UUID userId = UUID.randomUUID();
      TokenPayload accessPayload = new TokenPayload(userId, testEmail, "access-jti", "ACCESS", Instant.now(), Instant.now().plusSeconds(900));
      TokenPayload refreshPayload = new TokenPayload(userId, testEmail, "refresh-jti", "REFRESH", Instant.now(), Instant.now().plusSeconds(604800));

      when(jwtService.getRemainingExpirationMs("access-token")).thenReturn(900_000L);
      when(jwtService.parseAndValidateToken("access-token")).thenReturn(accessPayload);
      when(jwtService.parseAndValidateToken("refresh-token")).thenReturn(refreshPayload);

      authService.logout("access-token", "refresh-token");

      verify(tokenRevocationService).blacklistAccessToken("access-jti", 900_000L);
      verify(tokenRevocationService).revokeRefreshToken(userId, "refresh-jti");
    }

    @Test
    @DisplayName("logout with Bearer prefix strips prefix before parsing and blacklisting")
    void logout_whenAccessTokenHasBearerPrefix_stripsPrefix() {
      UUID userId = UUID.randomUUID();
      TokenPayload accessPayload = new TokenPayload(userId, testEmail, "access-jti", "ACCESS", Instant.now(), Instant.now().plusSeconds(900));

      when(jwtService.getRemainingExpirationMs("clean-access-token")).thenReturn(900_000L);
      when(jwtService.parseAndValidateToken("clean-access-token")).thenReturn(accessPayload);

      authService.logout("Bearer clean-access-token", null);

      verify(tokenRevocationService).blacklistAccessToken("access-jti", 900_000L);
    }

    @Test
    @DisplayName("logout when tokens null or blank handles gracefully")
    void logout_whenTokensNullOrBlank_handlesGracefully() {
      authService.logout(null, "");
      verify(tokenRevocationService, never()).blacklistAccessToken(anyString(), anyLong());
      verify(tokenRevocationService, never()).revokeRefreshToken(any(), anyString());
    }
  }

  @Nested
  @DisplayName("logoutAll")
  class LogoutAllTests {

    @Test
    @DisplayName("logoutAll blacklists access token and revokes all user sessions")
    void logoutAll_whenValidAccessToken_blacklistsAccessAndRevokesAllSessions() {
      UUID userId = UUID.randomUUID();
      TokenPayload accessPayload = new TokenPayload(userId, testEmail, "access-jti", "ACCESS", Instant.now(), Instant.now().plusSeconds(900));

      when(jwtService.getRemainingExpirationMs("access-token")).thenReturn(900_000L);
      when(jwtService.parseAndValidateToken("access-token")).thenReturn(accessPayload);

      authService.logoutAll(userId, "Bearer access-token");

      verify(tokenRevocationService).blacklistAccessToken("access-jti", 900_000L);
      verify(tokenRevocationService).revokeAllUserSessions(userId);
    }

    @Test
    @DisplayName("logoutAll when access token is null only revokes all user sessions")
    void logoutAll_whenAccessTokenNull_onlyRevokesAllSessions() {
      UUID userId = UUID.randomUUID();
      authService.logoutAll(userId, null);

      verify(tokenRevocationService, never()).blacklistAccessToken(anyString(), anyLong());
      verify(tokenRevocationService).revokeAllUserSessions(userId);
    }
  }

  @Nested
  @DisplayName("forgotPassword")
  class ForgotPasswordTests {

    @Test
    @DisplayName("forgotPassword when user not found returns silently without throwing or sending email")
    void forgotPassword_whenUserNotFound_returnsSilentlyWithoutSendingEmail() {
      when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

      authService.forgotPassword(new ForgotPasswordRequest("unknown@example.com"));

      verify(otpService, never()).checkCooldown(anyString(), any(OtpType.class));
      verify(otpService, never()).generateAndStoreOtp(anyString(), any(OtpType.class));
      verify(emailService, never()).sendForgotPasswordOtp(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("forgotPassword when cooldown active throws RateLimitException")
    void forgotPassword_whenCooldownActive_throwsRateLimitException() {
      User user = User.builder().email(testEmail).fullName("Nguyen Van A").build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      doThrow(new RateLimitException("Cooldown active"))
          .when(otpService).checkCooldown(testEmail, OtpType.FORGOT_PASSWORD);

      assertThatThrownBy(() -> authService.forgotPassword(new ForgotPasswordRequest(testEmail)))
          .isInstanceOf(RateLimitException.class);
      verify(otpService, never()).generateAndStoreOtp(anyString(), any(OtpType.class));
      verify(emailService, never()).sendForgotPasswordOtp(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("forgotPassword when user exists and cooldown clear generates OTP and sends email")
    void forgotPassword_whenUserExists_generatesOtpAndSendsEmail() {
      User user = User.builder().email(testEmail).fullName("Nguyen Van A").build();
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
      when(otpService.generateAndStoreOtp(testEmail, OtpType.FORGOT_PASSWORD)).thenReturn("998877");

      authService.forgotPassword(new ForgotPasswordRequest(testEmail));

      verify(otpService).checkCooldown(testEmail, OtpType.FORGOT_PASSWORD);
      verify(otpService).generateAndStoreOtp(testEmail, OtpType.FORGOT_PASSWORD);
      verify(emailService).sendForgotPasswordOtp(testEmail, "Nguyen Van A", "998877");
    }
  }

  @Nested
  @DisplayName("verifyForgotPasswordOtp")
  class VerifyForgotPasswordOtpTests {

    @Test
    @DisplayName("verifyForgotPasswordOtp when user not found throws ResourceNotFoundException")
    void verifyForgotPasswordOtp_whenUserNotFound_throwsResourceNotFoundException() {
      when(userRepository.existsByEmail(testEmail)).thenReturn(false);

      VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "123456");
      assertThatThrownBy(() -> authService.verifyForgotPasswordOtp(request))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("verifyForgotPasswordOtp when OTP invalid throws InvalidOtpException")
    void verifyForgotPasswordOtp_whenOtpInvalid_throwsInvalidOtpException() {
      when(userRepository.existsByEmail(testEmail)).thenReturn(true);
      when(otpService.verifyOtp(testEmail, OtpType.FORGOT_PASSWORD, "wrong-otp")).thenReturn(false);

      VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "wrong-otp");
      assertThatThrownBy(() -> authService.verifyForgotPasswordOtp(request))
          .isInstanceOf(InvalidOtpException.class);
      verify(otpService, never()).generateAndStorePasswordResetToken(anyString());
    }

    @Test
    @DisplayName("verifyForgotPasswordOtp when OTP valid generates and returns reset token")
    void verifyForgotPasswordOtp_whenOtpValid_returnsResetPasswordToken() {
      when(userRepository.existsByEmail(testEmail)).thenReturn(true);
      when(otpService.verifyOtp(testEmail, OtpType.FORGOT_PASSWORD, "123456")).thenReturn(true);
      when(otpService.generateAndStorePasswordResetToken(testEmail)).thenReturn("reset-token-uuid");

      VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "123456");
      VerifyResetPasswordOtpResponse response = authService.verifyForgotPasswordOtp(request);

      assertThat(response.email()).isEqualTo(testEmail);
      assertThat(response.resetToken()).isEqualTo("reset-token-uuid");
    }
  }

  @Nested
  @DisplayName("resetPassword")
  class ResetPasswordTests {

    @Test
    @DisplayName("resetPassword updates password and revokes all user sessions")
    void resetPassword_updatesPasswordAndRevokesAllUserSessions() {
      User user = User.builder().email("user@example.com").fullName("Test").build();
      user.setId(UUID.randomUUID());
      when(otpService.verifyPasswordResetToken("user@example.com", "valid-reset-token")).thenReturn(true);
      when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
      when(passwordEncoder.encode("NewPassword123@")).thenReturn("new-hash");

      authService.resetPassword(new ResetPasswordRequest("user@example.com", "valid-reset-token", "NewPassword123@"));

      assertThat(user.getPasswordHash()).isEqualTo("new-hash");
      verify(tokenRevocationService).revokeAllUserSessions(user.getId());
      verify(userRepository).save(user);
    }

    @Test
    @DisplayName("resetPassword when reset token is invalid or expired throws InvalidOtpException")
    void resetPassword_whenResetTokenInvalid_throwsInvalidOtpException() {
      when(otpService.verifyPasswordResetToken(testEmail, "expired-token")).thenReturn(false);

      ResetPasswordRequest request = new ResetPasswordRequest(testEmail, "expired-token", "NewPassword123@");
      assertThatThrownBy(() -> authService.resetPassword(request))
          .isInstanceOf(InvalidOtpException.class);
      verify(userRepository, never()).save(any(User.class));
      verify(tokenRevocationService, never()).revokeAllUserSessions(any());
    }

    @Test
    @DisplayName("resetPassword when user not found throws ResourceNotFoundException")
    void resetPassword_whenUserNotFound_throwsResourceNotFoundException() {
      when(otpService.verifyPasswordResetToken(testEmail, "valid-token")).thenReturn(true);
      when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

      ResetPasswordRequest request = new ResetPasswordRequest(testEmail, "valid-token", "NewPassword123@");
      assertThatThrownBy(() -> authService.resetPassword(request))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("changePassword")
  class ChangePasswordTests {

    @Test
    @DisplayName("changePassword when newPassword and confirmPassword do not match throws InvalidPasswordException")
    void changePassword_whenPasswordsDoNotMatch_throwsInvalidPasswordException() {
      UUID userId = UUID.randomUUID();
      ChangePasswordRequest request = new ChangePasswordRequest("OldPass123@", "NewPass123@", "DifferentPass123@");

      assertThatThrownBy(() -> authService.changePassword(userId, request))
          .isInstanceOf(InvalidPasswordException.class)
          .hasMessageContaining("match");
    }

    @Test
    @DisplayName("changePassword when newPassword is same as currentPassword throws InvalidPasswordException")
    void changePassword_whenNewPasswordSameAsCurrentPassword_throwsInvalidPasswordException() {
      UUID userId = UUID.randomUUID();
      ChangePasswordRequest request = new ChangePasswordRequest("SamePassword123@", "SamePassword123@", "SamePassword123@");

      assertThatThrownBy(() -> authService.changePassword(userId, request))
          .isInstanceOf(InvalidPasswordException.class)
          .hasMessageContaining("different");
    }

    @Test
    @DisplayName("changePassword when user not found throws ResourceNotFoundException")
    void changePassword_whenUserNotFound_throwsResourceNotFoundException() {
      UUID userId = UUID.randomUUID();
      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      ChangePasswordRequest request = new ChangePasswordRequest("OldPass123@", "NewPass123@", "NewPass123@");
      assertThatThrownBy(() -> authService.changePassword(userId, request))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("changePassword when currentPassword is incorrect throws InvalidPasswordException")
    void changePassword_whenCurrentPasswordIncorrect_throwsInvalidPasswordException() {
      UUID userId = UUID.randomUUID();
      User user = User.builder().id(userId).email(testEmail).passwordHash("current-hash").build();
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));
      when(passwordEncoder.matches("WrongCurrent123@", "current-hash")).thenReturn(false);

      ChangePasswordRequest request = new ChangePasswordRequest("WrongCurrent123@", "NewPass123@", "NewPass123@");
      assertThatThrownBy(() -> authService.changePassword(userId, request))
          .isInstanceOf(InvalidPasswordException.class)
          .hasMessageContaining("Current password is incorrect");
    }

    @Test
    @DisplayName("changePassword when valid updates password and revokes all user sessions")
    void changePassword_whenValid_updatesPasswordAndRevokesAllUserSessions() {
      UUID userId = UUID.randomUUID();
      User user = User.builder().id(userId).email(testEmail).passwordHash("current-hash").build();
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));
      when(passwordEncoder.matches("OldPass123@", "current-hash")).thenReturn(true);
      when(passwordEncoder.encode("NewPass123@")).thenReturn("new-hash");

      ChangePasswordRequest request = new ChangePasswordRequest("OldPass123@", "NewPass123@", "NewPass123@");
      authService.changePassword(userId, request);

      assertThat(user.getPasswordHash()).isEqualTo("new-hash");
      verify(userRepository).save(user);
      verify(tokenRevocationService).revokeAllUserSessions(userId);
    }
  }
}
