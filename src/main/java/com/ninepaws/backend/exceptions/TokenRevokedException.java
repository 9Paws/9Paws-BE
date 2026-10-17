package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public class TokenRevokedException extends AppException {

  public TokenRevokedException(String message) {
    super(message, HttpStatus.UNAUTHORIZED);
  }
}
