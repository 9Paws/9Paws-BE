package com.ninepaws.backend.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.dtos.common.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  @DisplayName("MethodArgumentNotValidException returns 400 with field errors map")
  void whenMethodArgumentNotValidException_returns400WithErrorsMap() {
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError = new FieldError("userDto", "email", "must be a well-formed email address");

    when(ex.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

    ResponseEntity<ApiResponse<Void>> res = handler.handleValidationExceptions(ex);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().errors()).isNotNull();
    assertThat(res.getBody().errors()).containsEntry("email", "must be a well-formed email address");
  }

  @Test
  @DisplayName("InvalidOtpException returns 400 with error message")
  void whenInvalidOtpException_returns400WithErrorMessage() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleInvalidOtpException(new InvalidOtpException("Mã OTP không chính xác"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Mã OTP không chính xác");
  }

  @Test
  @DisplayName("RateLimitException returns 429")
  void whenRateLimitException_returns429() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleRateLimitException(new RateLimitException("Vui lòng đợi 60 giây"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Vui lòng đợi 60 giây");
  }

  @Test
  @DisplayName("ResourceNotFoundException returns 404")
  void whenResourceNotFoundException_returns404() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleResourceNotFoundException(new ResourceNotFoundException("User not found"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("User not found");
  }

  @Test
  @DisplayName("DuplicateResourceException returns 409")
  void whenDuplicateResourceException_returns409() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleDuplicateResourceException(new DuplicateResourceException("Email already exists"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Email already exists");
  }

  @Test
  @DisplayName("TokenRevokedException returns 401")
  void whenTokenRevokedException_returns401() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleTokenRevokedException(new TokenRevokedException("Token has been revoked"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Token has been revoked");
  }

  @Test
  @DisplayName("AccountNotVerifiedException returns 403")
  void whenAccountNotVerifiedException_returns403() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleAccountNotVerifiedException(new AccountNotVerifiedException("Account is not verified"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Account is not verified");
  }

  @Test
  @DisplayName("InvalidPasswordException returns 400")
  void whenInvalidPasswordException_returns400() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleInvalidPasswordException(new InvalidPasswordException("Mật khẩu cũ không chính xác"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Mật khẩu cũ không chính xác");
  }

  @Test
  @DisplayName("BadCredentialsException returns 401")
  void whenBadCredentialsException_returns401() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleBadCredentialsException(new BadCredentialsException("Invalid username or password"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Invalid username or password");
  }

  @Test
  @DisplayName("AccessDeniedException returns 403")
  void whenAccessDeniedException_returns403() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleAccessDeniedException(new AccessDeniedException("Access denied"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Access denied");
  }

  @Test
  @DisplayName("Generic AppException returns mapped HttpStatus")
  void whenAppException_returnsMappedHttpStatus() {
    AppException ex = new AppException("Custom error", HttpStatus.PAYMENT_REQUIRED);
    ResponseEntity<ApiResponse<Void>> res = handler.handleAppException(ex);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).isEqualTo("Custom error");
  }

  @Test
  @DisplayName("Generic Exception returns 500 without leaking raw message")
  void whenGenericException_returns500WithSafeMessage() {
    Exception ex = new RuntimeException("DB Connection failed with sensitive details");
    ResponseEntity<ApiResponse<Void>> res = handler.handleGenericException(ex);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().message()).doesNotContain("sensitive details");
    assertThat(res.getBody().message()).isEqualTo("An internal server error occurred");
  }
}
