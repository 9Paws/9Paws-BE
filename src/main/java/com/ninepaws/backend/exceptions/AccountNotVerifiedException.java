package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.exceptions.error.AuthErrorCode;

public class AccountNotVerifiedException extends AppException {

  public AccountNotVerifiedException(String message) {
    super(AuthErrorCode.ACCOUNT_NOT_VERIFIED, message);
  }

  public AccountNotVerifiedException() {
    super(AuthErrorCode.ACCOUNT_NOT_VERIFIED);
  }
}
