package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends AppException {

  public DuplicateResourceException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
