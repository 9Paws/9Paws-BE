package com.ninepaws.backend.exceptions.error;

import com.ninepaws.backend.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements AppErrorCode {
  INVALID_CREDENTIALS("AUTH_101", "Invalid email or password", HttpStatus.UNAUTHORIZED),
  INVALID_OTP("AUTH_102", "Invalid or expired OTP", HttpStatus.BAD_REQUEST),
  ACCOUNT_NOT_VERIFIED(
      "AUTH_103",
      "Account is not verified. Please verify your email via OTP before logging in.",
      HttpStatus.FORBIDDEN),
  TOKEN_REVOKED("AUTH_104", "Token has been revoked or is invalid", HttpStatus.UNAUTHORIZED),
  INVALID_PASSWORD("AUTH_105", "Invalid password provided", HttpStatus.BAD_REQUEST),
  RATE_LIMIT_EXCEEDED(
      "AUTH_106", "Too many requests. Please wait before retrying.", HttpStatus.TOO_MANY_REQUESTS);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
