package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.exceptions.error.UserErrorCode;

public class ResourceNotFoundException extends AppException {

  public ResourceNotFoundException(String message) {
    super(UserErrorCode.USER_NOT_FOUND, message);
  }

  public ResourceNotFoundException() {
    super(UserErrorCode.USER_NOT_FOUND);
  }
}
