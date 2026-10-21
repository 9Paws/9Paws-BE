package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public class AppException extends BaseAppException {

  public AppException(String message, HttpStatus status) {
    super(createErrorCode(status, message), message);
  }

  public AppException(String message, HttpStatus status, Throwable cause) {
    super(createErrorCode(status, message), message, cause);
  }

  public AppException(AppErrorCode errorCode) {
    super(errorCode);
  }

  public AppException(AppErrorCode errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public HttpStatus getStatus() {
    return getErrorCode().getHttpStatus();
  }

  private static AppErrorCode createErrorCode(HttpStatus status, String message) {
    String code = status.is5xxServerError() ? "SYS_" + status.value() : "WEB_" + status.value();
    return new AppErrorCode() {
      @Override
      public String getErrorCode() {
        return code;
      }

      @Override
      public String getMessage() {
        return message;
      }

      @Override
      public HttpStatus getHttpStatus() {
        return status;
      }
    };
  }
}
