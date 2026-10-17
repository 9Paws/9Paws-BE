package com.ninepaws.backend.dtos.common;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(
    boolean success, String message, T data, Map<String, String> errors, Instant timestamp) {

  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(true, message, data, null, Instant.now());
  }

  public static <T> ApiResponse<T> error(String message, Map<String, String> errors) {
    return new ApiResponse<>(false, message, null, errors, Instant.now());
  }
}
