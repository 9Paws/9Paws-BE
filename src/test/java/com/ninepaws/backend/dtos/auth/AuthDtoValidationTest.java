package com.ninepaws.backend.dtos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninepaws.backend.dtos.common.ApiResponse;
import com.ninepaws.backend.enums.OtpType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthDtoValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Nested
  @DisplayName("RegisterRequest validation")
  class RegisterRequestTests {

    @Test
    void whenRegisterRequestValid_thenValidationSucceeds() {
      RegisterRequest request =
          new RegisterRequest("Test User", "user@example.com", "Password123@");
      Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
      assertThat(violations).isEmpty();
    }

    @Test
    void whenRegisterRequestInvalidEmail_thenValidationFails() {
      RegisterRequest request = new RegisterRequest("Test User", "invalid-email", "Password123@");
      Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void whenRegisterRequestWeakPassword_thenValidationFails() {
      RegisterRequest request = new RegisterRequest("Test User", "user@example.com", "weak");
      Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void whenRegisterRequestBlankFullName_thenValidationFails() {
      RegisterRequest request = new RegisterRequest("   ", "user@example.com", "Password123@");
      Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    }

    @Test
    void whenRegisterRequestFullNameExceeds100_thenValidationFails() {
      String longName = "A".repeat(101);
      RegisterRequest request = new RegisterRequest(longName, "user@example.com", "Password123@");
      Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    }
  }

  @Nested
  @DisplayName("LoginRequest validation")
  class LoginRequestTests {

    @Test
    void whenLoginRequestValid_thenValidationSucceeds() {
      LoginRequest request = new LoginRequest("user@example.com", "Password123");
      Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
      assertThat(violations).isEmpty();
    }

    @Test
    void whenLoginRequestBlankPassword_thenValidationFails() {
      LoginRequest request = new LoginRequest("user@example.com", "");
      Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void whenLoginRequestInvalidEmail_thenValidationFails() {
      LoginRequest request = new LoginRequest("not-an-email", "Password123");
      Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }
  }

  @Nested
  @DisplayName("VerifyOtpRequest validation")
  class VerifyOtpRequestTests {

    @Test
    void whenVerifyOtpRequestValid_thenValidationSucceeds() {
      VerifyOtpRequest request = new VerifyOtpRequest("user@example.com", "123456");
      Set<ConstraintViolation<VerifyOtpRequest>> violations = validator.validate(request);
      assertThat(violations).isEmpty();
    }

    @Test
    void whenVerifyOtpRequestInvalidLength_thenValidationFails() {
      VerifyOtpRequest request = new VerifyOtpRequest("user@example.com", "123");
      Set<ConstraintViolation<VerifyOtpRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("otp"));
    }

    @Test
    void whenVerifyOtpRequestNonNumeric_thenValidationFails() {
      VerifyOtpRequest request = new VerifyOtpRequest("user@example.com", "12345a");
      Set<ConstraintViolation<VerifyOtpRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("otp"));
    }
  }

  @Nested
  @DisplayName("ResendOtpRequest validation")
  class ResendOtpRequestTests {

    @Test
    void whenResendOtpRequestValid_thenValidationSucceeds() {
      ResendOtpRequest request = new ResendOtpRequest("user@example.com", OtpType.REGISTER);
      Set<ConstraintViolation<ResendOtpRequest>> violations = validator.validate(request);
      assertThat(violations).isEmpty();
    }

    @Test
    void whenResendOtpRequestNullType_thenValidationFails() {
      ResendOtpRequest request = new ResendOtpRequest("user@example.com", null);
      Set<ConstraintViolation<ResendOtpRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
    }
  }

  @Nested
  @DisplayName("RefreshTokenRequest validation")
  class RefreshTokenRequestTests {

    @Test
    void whenRefreshTokenRequestBlank_thenValidationFails() {
      RefreshTokenRequest request = new RefreshTokenRequest("");
      Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }
  }

  @Nested
  @DisplayName("ForgotPasswordRequest validation")
  class ForgotPasswordRequestTests {

    @Test
    void whenForgotPasswordRequestValid_thenValidationSucceeds() {
      ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");
      Set<ConstraintViolation<ForgotPasswordRequest>> violations = validator.validate(request);
      assertThat(violations).isEmpty();
    }

    @Test
    void whenForgotPasswordRequestInvalidEmail_thenValidationFails() {
      ForgotPasswordRequest request = new ForgotPasswordRequest("invalid");
      Set<ConstraintViolation<ForgotPasswordRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }
  }

  @Nested
  @DisplayName("ResetPasswordRequest validation")
  class ResetPasswordRequestTests {

    @Test
    void whenResetPasswordRequestValid_thenValidationSucceeds() {
      ResetPasswordRequest request =
          new ResetPasswordRequest("user@example.com", "reset-token", "NewPass123");
      Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
      assertThat(violations).isEmpty();
    }

    @Test
    void whenResetPasswordRequestWeakPassword_thenValidationFails() {
      ResetPasswordRequest request =
          new ResetPasswordRequest("user@example.com", "reset-token", "weak");
      Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }
  }

  @Nested
  @DisplayName("ChangePasswordRequest validation")
  class ChangePasswordRequestTests {

    @Test
    void whenChangePasswordRequestValid_thenValidationSucceeds() {
      ChangePasswordRequest request =
          new ChangePasswordRequest("OldPass123", "NewPass123", "NewPass123");
      Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
      assertThat(violations).isEmpty();
    }

    @Test
    void whenChangePasswordRequestWeakNewPassword_thenValidationFails() {
      ChangePasswordRequest request = new ChangePasswordRequest("OldPass123", "short", "short");
      Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }
  }

  @Nested
  @DisplayName("Response DTOs and ApiResponse tests")
  class ResponseDtoTests {

    @Test
    void testAuthResponseAndUserResponse() {
      UUID id = UUID.randomUUID();
      UserResponse user =
          new UserResponse(
              id,
              "Test User",
              "user@example.com",
              "+1234567890",
              "https://avatar.png",
              true,
              "USD",
              "en");
      AuthResponse authResponse =
          new AuthResponse("access-token", "refresh-token", "Bearer", 3600L, user);

      assertThat(authResponse.accessToken()).isEqualTo("access-token");
      assertThat(authResponse.refreshToken()).isEqualTo("refresh-token");
      assertThat(authResponse.tokenType()).isEqualTo("Bearer");
      assertThat(authResponse.expiresIn()).isEqualTo(3600L);
      assertThat(authResponse.user()).isEqualTo(user);
      assertThat(user.emailVerified()).isTrue();
    }

    @Test
    void testVerifyResetPasswordOtpResponse() {
      VerifyResetPasswordOtpResponse response =
          new VerifyResetPasswordOtpResponse("user@example.com", "reset-token-xyz");
      assertThat(response.email()).isEqualTo("user@example.com");
      assertThat(response.resetToken()).isEqualTo("reset-token-xyz");
    }

    @Test
    void testApiResponseSuccess() {
      ApiResponse<String> response = ApiResponse.success("Operation successful", "data-payload");
      assertThat(response.success()).isTrue();
      assertThat(response.message()).isEqualTo("Operation successful");
      assertThat(response.data()).isEqualTo("data-payload");
      assertThat(response.errors()).isNull();
      assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void testApiResponseError() {
      Map<String, String> errors = Map.of("field", "must not be blank");
      ApiResponse<Void> response = ApiResponse.error("Validation failed", errors);
      assertThat(response.success()).isFalse();
      assertThat(response.message()).isEqualTo("Validation failed");
      assertThat(response.data()).isNull();
      assertThat(response.errors()).isEqualTo(errors);
      assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
    }
  }
}
