package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.exceptions.error.AuthErrorCode;

public class TokenRevokedException extends AppException {

  public TokenRevokedException(String message) {
    super(AuthErrorCode.TOKEN_REVOKED, message);
  }

  public TokenRevokedException() {
    super(AuthErrorCode.TOKEN_REVOKED);
  }
}
