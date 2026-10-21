package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.exceptions.error.UserErrorCode;

public class DuplicateResourceException extends AppException {

  public DuplicateResourceException(String message) {
    super(UserErrorCode.USER_ALREADY_EXISTS, message);
  }

  public DuplicateResourceException() {
    super(UserErrorCode.USER_ALREADY_EXISTS);
  }
}
