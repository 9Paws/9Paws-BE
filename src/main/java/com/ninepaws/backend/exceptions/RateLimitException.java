package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.exceptions.error.AuthErrorCode;

public class RateLimitException extends AppException {

  public RateLimitException(String message) {
    super(AuthErrorCode.RATE_LIMIT_EXCEEDED, message);
  }

  public RateLimitException() {
    super(AuthErrorCode.RATE_LIMIT_EXCEEDED);
  }
}
