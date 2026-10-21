package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.exceptions.error.AuthErrorCode;

public class InvalidOtpException extends AppException {

  public InvalidOtpException(String message) {
    super(AuthErrorCode.INVALID_OTP, message);
  }

  public InvalidOtpException() {
    super(AuthErrorCode.INVALID_OTP);
  }
}
