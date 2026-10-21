package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public interface AppErrorCode {
  String getErrorCode();

  String getMessage();

  HttpStatus getHttpStatus();
}
