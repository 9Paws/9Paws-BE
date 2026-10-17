package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidPasswordException extends AppException {

  public InvalidPasswordException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
