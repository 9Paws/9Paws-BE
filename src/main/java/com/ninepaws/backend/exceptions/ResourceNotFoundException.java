package com.ninepaws.backend.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {

  public ResourceNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
