package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidOtpException extends AppException {

  public InvalidOtpException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
