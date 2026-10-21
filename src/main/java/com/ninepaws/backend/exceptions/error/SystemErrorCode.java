package com.ninepaws.backend.exceptions.error;

import com.ninepaws.backend.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SystemErrorCode implements AppErrorCode {
  INTERNAL_SERVER_ERROR(
      "SYS_500", "An unexpected system error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
  DATABASE_ERROR(
      "SYS_501", "Database operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
  EXTERNAL_SERVICE_ERROR(
      "SYS_502", "External service communication failed", HttpStatus.BAD_GATEWAY);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
