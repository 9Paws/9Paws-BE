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
      "AUTH_106", "Too many requests. Please wait before retrying.", HttpStatus.TOO_MANY_REQUESTS),
  AUTH_ACCOUNT_LINKING_REQUIRED(
      "AUTH_107",
      "Email đã được đăng ký bằng mật khẩu. Vui lòng đăng nhập bằng tài khoản hiện tại rồi thực hiện liên kết Google.",
      HttpStatus.CONFLICT),
  AUTH_ACCOUNT_ALREADY_LINKED(
      "AUTH_108", "Tài khoản hiện tại đã được liên kết với Google.", HttpStatus.BAD_REQUEST),
  AUTH_EMAIL_MISMATCH(
      "AUTH_109",
      "Email tài khoản Google không khớp với email tài khoản hiện tại.",
      HttpStatus.BAD_REQUEST),
  AUTH_OAUTH_ACCOUNT_ALREADY_IN_USE(
      "AUTH_110",
      "Tài khoản Google này đã được liên kết với một tài khoản khác.",
      HttpStatus.CONFLICT),
  INVALID_GOOGLE_TOKEN(
      "AUTH_111", "Token Google không hợp lệ hoặc đã hết hạn.", HttpStatus.UNAUTHORIZED);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
