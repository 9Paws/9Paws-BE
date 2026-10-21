package com.ninepaws.backend.dtos.response;

import com.ninepaws.backend.dtos.response.base.DetailedErrorResponse;
import com.ninepaws.backend.dtos.response.base.ErrorResponse;
import com.ninepaws.backend.dtos.response.base.PageResponse;
import com.ninepaws.backend.dtos.response.base.SingleResponse;
import com.ninepaws.backend.dtos.response.base.SuccessResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ResponseFactory {

  public ResponseEntity<SuccessResponse> success(HttpStatus httpStatus, String message) {
    SuccessResponse response =
        SuccessResponse.builder().httpStatus(httpStatus.value()).message(message).build();
    return ResponseEntity.status(httpStatus).body(response);
  }

  public ResponseEntity<SuccessResponse> success(String message) {
    return success(HttpStatus.OK, message);
  }

  public <T> ResponseEntity<SingleResponse<T>> success(
      HttpStatus httpStatus, T data, String message) {
    SingleResponse<T> response =
        SingleResponse.<T>builder()
            .httpStatus(httpStatus.value())
            .message(message)
            .data(data)
            .build();
    return ResponseEntity.status(httpStatus).body(response);
  }

  public <T> ResponseEntity<SingleResponse<T>> success(T data, String message) {
    return success(HttpStatus.OK, data, message);
  }

  public <T> ResponseEntity<SingleResponse<PageResponse<T>>> successPage(
      Page<T> pageData, String message) {
    return success(HttpStatus.OK, PageResponse.from(pageData), message);
  }

  public ErrorResponse createErrorResponse(
      HttpStatus httpStatus, String errorCode, String message, String path) {
    return ErrorResponse.builder()
        .httpStatus(httpStatus.value())
        .errorCode(errorCode)
        .message(message)
        .timestamp(Instant.now())
        .path(path)
        .build();
  }

  public DetailedErrorResponse createDetailedErrorResponse(
      HttpStatus httpStatus,
      String errorCode,
      String message,
      String path,
      Map<String, List<String>> items) {
    return DetailedErrorResponse.builder()
        .httpStatus(httpStatus.value())
        .errorCode(errorCode)
        .message(message)
        .path(path)
        .timestamp(Instant.now())
        .items(items)
        .build();
  }
}
