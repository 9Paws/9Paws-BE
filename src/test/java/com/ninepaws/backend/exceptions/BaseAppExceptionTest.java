package com.ninepaws.backend.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninepaws.backend.exceptions.error.AuthErrorCode;
import com.ninepaws.backend.exceptions.error.SystemErrorCode;
import com.ninepaws.backend.exceptions.error.UserErrorCode;
import com.ninepaws.backend.exceptions.error.WebErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BaseAppExceptionTest {

  @Test
  @DisplayName("BaseAppException with default error code message")
  void whenCreatedWithErrorCode_usesDefaultMessage() {
    BaseAppException ex = new BaseAppException(UserErrorCode.USER_NOT_FOUND);

    assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("User not found");
    assertThat(ex.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getErrorCode().getErrorCode()).isEqualTo("USER_101");
  }

  @Test
  @DisplayName("BaseAppException with custom message")
  void whenCreatedWithCustomMessage_overridesDefaultMessage() {
    BaseAppException ex =
        new BaseAppException(UserErrorCode.USER_NOT_FOUND, "User ID 123 does not exist");

    assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("User ID 123 does not exist");
  }

  @Test
  @DisplayName("BaseAppException with formatted args")
  void whenCreatedWithArgs_formatsMessageCorrectly() {
    AppErrorCode templateCode =
        new AppErrorCode() {
          @Override
          public String getErrorCode() {
            return "TEST_001";
          }

          @Override
          public String getMessage() {
            return "Item with id %s not found in category %s";
          }

          @Override
          public HttpStatus getHttpStatus() {
            return HttpStatus.NOT_FOUND;
          }
        };

    BaseAppException ex = new BaseAppException(templateCode, 42, "electronics");
    assertThat(ex.getMessage()).isEqualTo("Item with id 42 not found in category electronics");
  }

  @Test
  @DisplayName("BaseAppException with mismatched args does not crash (safe formatting)")
  void whenArgsMismatched_doesNotThrowFormatException() {
    AppErrorCode templateCode =
        new AppErrorCode() {
          @Override
          public String getErrorCode() {
            return "TEST_002";
          }

          @Override
          public String getMessage() {
            return "User with id %d";
          }

          @Override
          public HttpStatus getHttpStatus() {
            return HttpStatus.BAD_REQUEST;
          }
        };

    // Passing invalid format string argument via varargs array (string instead of integer)
    BaseAppException ex = new BaseAppException(templateCode, new Object[] {"not-a-number"});
    assertThat(ex.getMessage()).isEqualTo("User with id %d");
  }

  @Test
  @DisplayName("Existing domain exceptions extend BaseAppException with proper codes")
  void verifyDomainExceptionsHierarchy() {
    InvalidOtpException invalidOtp = new InvalidOtpException("Custom OTP message");
    assertThat(invalidOtp).isInstanceOf(BaseAppException.class);
    assertThat(invalidOtp.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_OTP);
    assertThat(invalidOtp.getMessage()).isEqualTo("Custom OTP message");

    RateLimitException rateLimit = new RateLimitException("Please wait");
    assertThat(rateLimit).isInstanceOf(BaseAppException.class);
    assertThat(rateLimit.getErrorCode()).isEqualTo(AuthErrorCode.RATE_LIMIT_EXCEEDED);

    DuplicateResourceException duplicate = new DuplicateResourceException("Email taken");
    assertThat(duplicate).isInstanceOf(BaseAppException.class);
    assertThat(duplicate.getErrorCode()).isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);

    ResourceNotFoundException notFound = new ResourceNotFoundException("Not found");
    assertThat(notFound).isInstanceOf(BaseAppException.class);
    assertThat(notFound.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);

    TokenRevokedException tokenRevoked = new TokenRevokedException("Revoked");
    assertThat(tokenRevoked).isInstanceOf(BaseAppException.class);
    assertThat(tokenRevoked.getErrorCode()).isEqualTo(AuthErrorCode.TOKEN_REVOKED);

    AccountNotVerifiedException notVerified = new AccountNotVerifiedException("Not verified");
    assertThat(notVerified).isInstanceOf(BaseAppException.class);
    assertThat(notVerified.getErrorCode()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_VERIFIED);

    InvalidPasswordException invalidPwd = new InvalidPasswordException("Bad password");
    assertThat(invalidPwd).isInstanceOf(BaseAppException.class);
    assertThat(invalidPwd.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_PASSWORD);
  }

  @Test
  @DisplayName("Verify WebErrorCode and SystemErrorCode enum values")
  void verifyStandardErrorCodes() {
    assertThat(WebErrorCode.VALIDATION_FAILED.getErrorCode()).isEqualTo("WEB_400_VAL");
    assertThat(WebErrorCode.VALIDATION_FAILED.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(SystemErrorCode.INTERNAL_SERVER_ERROR.getErrorCode()).isEqualTo("SYS_500");
    assertThat(SystemErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
