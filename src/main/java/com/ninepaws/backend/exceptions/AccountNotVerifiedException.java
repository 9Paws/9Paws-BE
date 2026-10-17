package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public class AccountNotVerifiedException extends AppException {

  public AccountNotVerifiedException(String message) {
    super(message, HttpStatus.FORBIDDEN);
  }
}
