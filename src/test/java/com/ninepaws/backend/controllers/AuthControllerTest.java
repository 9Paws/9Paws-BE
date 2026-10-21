package com.ninepaws.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import com.ninepaws.backend.enums.OtpType;
import com.ninepaws.backend.exceptions.DuplicateResourceException;
import com.ninepaws.backend.exceptions.GlobalExceptionHandler;
import com.ninepaws.backend.exceptions.InvalidOtpException;
import com.ninepaws.backend.exceptions.RateLimitException;
import com.ninepaws.backend.security.UserPrincipal;
import com.ninepaws.backend.services.AuthService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;

  @InjectMocks private AuthController authController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();

    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void setAuthenticatedUser(UUID userId, String email) {
    UserPrincipal principal =
        new UserPrincipal(
            userId,
            email,
            "hashedPassword",
            true,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Nested
  @DisplayName("POST /api/v1/auth/register")
  class RegisterTests {

    @Test
    @DisplayName("register returns 201 Created when request is valid")
    void register_returns201Created() throws Exception {
      RegisterRequest req = new RegisterRequest("Test User", "test@ninepaws.com", "Password123@");

      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.httpStatus").value(201))
          .andExpect(jsonPath("$.message").isNotEmpty());

      verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("register returns 409 Conflict when email is duplicate")
    void register_whenDuplicateEmail_returns409() throws Exception {
      RegisterRequest req = new RegisterRequest("Test User", "test@ninepaws.com", "Password123@");
      doThrow(new DuplicateResourceException("Email already exists"))
          .when(authService)
          .register(any(RegisterRequest.class));

      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.httpStatus").value(409))
          .andExpect(jsonPath("$.errorCode").value("USER_102"))
          .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    @DisplayName("register returns 400 Bad Request when request body is invalid")
    void register_whenInvalidBody_returns400() throws Exception {
      RegisterRequest invalidReq = new RegisterRequest("", "not-an-email", "short");

      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidReq)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"))
          .andExpect(jsonPath("$.items").isMap());
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/verify-registration-otp")
  class VerifyRegistrationOtpTests {

    @Test
    @DisplayName("verify-registration-otp returns 200 OK and AuthResponse when valid")
    void verifyRegistrationOtp_returns200AndAuthResponse() throws Exception {
      VerifyOtpRequest req = new VerifyOtpRequest("test@ninepaws.com", "123456");
      AuthResponse mockAuth =
          new AuthResponse(
              "access-token",
              "refresh-token",
              "Bearer",
              1800L,
              new UserResponse(
                  UUID.randomUUID(),
                  "Test User",
                  "test@ninepaws.com",
                  null,
                  null,
                  true,
                  "VND",
                  "vi"));
      when(authService.verifyRegistrationOtp(any(VerifyOtpRequest.class))).thenReturn(mockAuth);

      mockMvc
          .perform(
              post("/api/v1/auth/verify-registration-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200))
          .andExpect(jsonPath("$.data.accessToken").value("access-token"))
          .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
          .andExpect(jsonPath("$.data.expiresIn").value(1800L));

      verify(authService).verifyRegistrationOtp(any(VerifyOtpRequest.class));
    }

    @Test
    @DisplayName("verify-registration-otp returns 400 Bad Request when OTP is invalid")
    void verifyRegistrationOtp_whenInvalidOtp_returns400() throws Exception {
      VerifyOtpRequest req = new VerifyOtpRequest("test@ninepaws.com", "123456");
      when(authService.verifyRegistrationOtp(any(VerifyOtpRequest.class)))
          .thenThrow(new InvalidOtpException("Invalid or expired OTP"));

      mockMvc
          .perform(
              post("/api/v1/auth/verify-registration-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("AUTH_102"))
          .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
    }

    @Test
    @DisplayName("verify-registration-otp returns 400 when OTP format is invalid")
    void verifyRegistrationOtp_whenOtpFormatInvalid_returns400() throws Exception {
      VerifyOtpRequest req = new VerifyOtpRequest("test@ninepaws.com", "12");

      mockMvc
          .perform(
              post("/api/v1/auth/verify-registration-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/resend-otp")
  class ResendOtpTests {

    @Test
    @DisplayName("resend-otp returns 200 OK when request is valid")
    void resendOtp_returns200() throws Exception {
      ResendOtpRequest req = new ResendOtpRequest("test@ninepaws.com", OtpType.REGISTER);

      mockMvc
          .perform(
              post("/api/v1/auth/resend-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200));

      verify(authService).resendOtp(any(ResendOtpRequest.class));
    }

    @Test
    @DisplayName("resend-otp returns 429 when rate limited")
    void resendOtp_whenRateLimited_returns429() throws Exception {
      ResendOtpRequest req = new ResendOtpRequest("test@ninepaws.com", OtpType.FORGOT_PASSWORD);
      doThrow(new RateLimitException("Please wait before requesting another OTP"))
          .when(authService)
          .resendOtp(any(ResendOtpRequest.class));

      mockMvc
          .perform(
              post("/api/v1/auth/resend-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.httpStatus").value(429))
          .andExpect(jsonPath("$.errorCode").value("AUTH_106"))
          .andExpect(jsonPath("$.message").value("Please wait before requesting another OTP"));
    }

    @Test
    @DisplayName("resend-otp returns 400 when type is missing")
    void resendOtp_whenTypeMissing_returns400() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/auth/resend-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"test@ninepaws.com\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/login")
  class LoginTests {

    @Test
    @DisplayName("login returns 200 OK and AuthResponse")
    void login_returns200AndAuthResponse() throws Exception {
      LoginRequest req = new LoginRequest("test@ninepaws.com", "Password123@");
      AuthResponse mockAuth =
          new AuthResponse(
              "access-token",
              "refresh-token",
              "Bearer",
              1800L,
              new UserResponse(
                  UUID.randomUUID(),
                  "Test User",
                  "test@ninepaws.com",
                  null,
                  null,
                  true,
                  "VND",
                  "vi"));

      when(authService.login(any(LoginRequest.class))).thenReturn(mockAuth);

      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200))
          .andExpect(jsonPath("$.data.accessToken").value("access-token"))
          .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));

      verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("login returns 401 Unauthorized when credentials are bad")
    void login_whenBadCredentials_returns401() throws Exception {
      LoginRequest req = new LoginRequest("test@ninepaws.com", "WrongPassword");
      when(authService.login(any(LoginRequest.class)))
          .thenThrow(new BadCredentialsException("Invalid email or password"));

      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.httpStatus").value(401))
          .andExpect(jsonPath("$.errorCode").value("WEB_401"))
          .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("login returns 400 Bad Request when body is blank")
    void login_whenBlankFields_returns400() throws Exception {
      LoginRequest req = new LoginRequest("", "");

      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/refresh-token")
  class RefreshTokenTests {

    @Test
    @DisplayName("refresh-token returns 200 OK and AuthResponse")
    void refreshToken_returns200AndAuthResponse() throws Exception {
      RefreshTokenRequest req = new RefreshTokenRequest("valid-refresh-token");
      AuthResponse mockAuth =
          new AuthResponse(
              "new-access-token",
              "new-refresh-token",
              "Bearer",
              1800L,
              new UserResponse(
                  UUID.randomUUID(),
                  "Test User",
                  "test@ninepaws.com",
                  null,
                  null,
                  true,
                  "VND",
                  "vi"));
      when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(mockAuth);

      mockMvc
          .perform(
              post("/api/v1/auth/refresh-token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200))
          .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
          .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));

      verify(authService).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    @DisplayName("refresh-token returns 400 Bad Request when refreshToken is blank")
    void refreshToken_whenBlankToken_returns400() throws Exception {
      RefreshTokenRequest req = new RefreshTokenRequest("");

      mockMvc
          .perform(
              post("/api/v1/auth/refresh-token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/logout")
  class LogoutTests {

    @Test
    @DisplayName("logout returns 200 OK with Authorization header and refresh token body")
    void logout_withBearerTokenAndRefreshToken_returns200() throws Exception {
      RefreshTokenRequest req = new RefreshTokenRequest("my-refresh-token");

      mockMvc
          .perform(
              post("/api/v1/auth/logout")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer my-access-token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200));

      verify(authService).logout(eq("Bearer my-access-token"), eq("my-refresh-token"));
    }

    @Test
    @DisplayName("logout returns 200 OK without body")
    void logout_withoutBody_returns200() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/auth/logout")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer my-access-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200));

      verify(authService).logout(eq("Bearer my-access-token"), isNull());
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/logout-all")
  class LogoutAllTests {

    @Test
    @DisplayName("logout-all returns 200 OK with authenticated user")
    void logoutAll_withAuthenticatedUser_returns200() throws Exception {
      UUID userId = UUID.randomUUID();
      setAuthenticatedUser(userId, "test@ninepaws.com");

      mockMvc
          .perform(
              post("/api/v1/auth/logout-all")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer my-access-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200));

      verify(authService).logoutAll(eq(userId), eq("Bearer my-access-token"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/forgot-password")
  class ForgotPasswordTests {

    @Test
    @DisplayName("forgot-password returns 200 OK when request is valid")
    void forgotPassword_returns200() throws Exception {
      ForgotPasswordRequest req = new ForgotPasswordRequest("test@ninepaws.com");

      mockMvc
          .perform(
              post("/api/v1/auth/forgot-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200));

      verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    @DisplayName("forgot-password returns 400 when email is invalid")
    void forgotPassword_whenInvalidEmail_returns400() throws Exception {
      ForgotPasswordRequest req = new ForgotPasswordRequest("invalid-email");

      mockMvc
          .perform(
              post("/api/v1/auth/forgot-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/verify-forgot-password-otp")
  class VerifyForgotPasswordOtpTests {

    @Test
    @DisplayName("verify-forgot-password-otp returns 200 OK and reset token")
    void verifyForgotPasswordOtp_returns200AndResetToken() throws Exception {
      VerifyOtpRequest req = new VerifyOtpRequest("test@ninepaws.com", "654321");
      VerifyResetPasswordOtpResponse response =
          new VerifyResetPasswordOtpResponse("test@ninepaws.com", "reset-token-uuid");
      when(authService.verifyForgotPasswordOtp(any(VerifyOtpRequest.class))).thenReturn(response);

      mockMvc
          .perform(
              post("/api/v1/auth/verify-forgot-password-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200))
          .andExpect(jsonPath("$.data.email").value("test@ninepaws.com"))
          .andExpect(jsonPath("$.data.resetToken").value("reset-token-uuid"));

      verify(authService).verifyForgotPasswordOtp(any(VerifyOtpRequest.class));
    }

    @Test
    @DisplayName("verify-forgot-password-otp returns 400 when OTP is invalid")
    void verifyForgotPasswordOtp_whenInvalidOtp_returns400() throws Exception {
      VerifyOtpRequest req = new VerifyOtpRequest("test@ninepaws.com", "654321");
      when(authService.verifyForgotPasswordOtp(any(VerifyOtpRequest.class)))
          .thenThrow(new InvalidOtpException("Invalid or expired OTP"));

      mockMvc
          .perform(
              post("/api/v1/auth/verify-forgot-password-otp")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("AUTH_102"))
          .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/reset-password")
  class ResetPasswordTests {

    @Test
    @DisplayName("reset-password returns 200 OK when request is valid")
    void resetPassword_returns200() throws Exception {
      ResetPasswordRequest req =
          new ResetPasswordRequest("test@ninepaws.com", "reset-token", "NewPassword123@");

      mockMvc
          .perform(
              post("/api/v1/auth/reset-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200));

      verify(authService).resetPassword(any(ResetPasswordRequest.class));
    }

    @Test
    @DisplayName("reset-password returns 400 when new password format is invalid")
    void resetPassword_whenWeakPassword_returns400() throws Exception {
      ResetPasswordRequest req =
          new ResetPasswordRequest("test@ninepaws.com", "reset-token", "short");

      mockMvc
          .perform(
              post("/api/v1/auth/reset-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/change-password")
  class ChangePasswordTests {

    @Test
    @DisplayName("change-password returns 200 OK with authenticated user and valid request")
    void changePassword_withAuthenticatedUser_returns200() throws Exception {
      UUID userId = UUID.randomUUID();
      setAuthenticatedUser(userId, "test@ninepaws.com");

      ChangePasswordRequest req =
          new ChangePasswordRequest("OldPassword123@", "NewPassword123@", "NewPassword123@");

      mockMvc
          .perform(
              post("/api/v1/auth/change-password")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer my-access-token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.httpStatus").value(200));

      verify(authService)
          .changePassword(
              eq(userId), eq("Bearer my-access-token"), any(ChangePasswordRequest.class));
    }

    @Test
    @DisplayName("change-password returns 400 when new password is weak")
    void changePassword_whenWeakPassword_returns400() throws Exception {
      UUID userId = UUID.randomUUID();
      setAuthenticatedUser(userId, "test@ninepaws.com");

      ChangePasswordRequest req = new ChangePasswordRequest("OldPassword123@", "weak", "weak");

      mockMvc
          .perform(
              post("/api/v1/auth/change-password")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer my-access-token")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.httpStatus").value(400))
          .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
    }
  }
}
