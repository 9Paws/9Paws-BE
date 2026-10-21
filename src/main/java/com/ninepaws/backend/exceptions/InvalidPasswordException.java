package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.exceptions.error.AuthErrorCode;

public class InvalidPasswordException extends AppException {

  public InvalidPasswordException(String message) {
    super(AuthErrorCode.INVALID_PASSWORD, message);
  }

  public InvalidPasswordException() {
    super(AuthErrorCode.INVALID_PASSWORD);
  }
}
