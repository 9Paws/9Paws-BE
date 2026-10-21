package com.ninepaws.backend.dtos.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninepaws.backend.dtos.response.base.DetailedErrorResponse;
import com.ninepaws.backend.dtos.response.base.ErrorResponse;
import com.ninepaws.backend.dtos.response.base.PageResponse;
import com.ninepaws.backend.dtos.response.base.SingleResponse;
import com.ninepaws.backend.dtos.response.base.SuccessResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ResponseFactoryTest {

  private ResponseFactory responseFactory;

  @BeforeEach
  void setUp() {
    responseFactory = new ResponseFactory();
  }

  @Test
  @DisplayName("success(String message) returns SuccessResponse with HTTP 200")
  void successWithMessage_returnsSuccessResponse() {
    ResponseEntity<SuccessResponse> response = responseFactory.success("Operation successful");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(200);
    assertThat(response.getBody().getMessage()).isEqualTo("Operation successful");
  }

  @Test
  @DisplayName("success(HttpStatus, String) returns SuccessResponse with specified status")
  void successWithStatusAndMessage_returnsSuccessResponse() {
    ResponseEntity<SuccessResponse> response =
        responseFactory.success(HttpStatus.CREATED, "Resource created");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(201);
    assertThat(response.getBody().getMessage()).isEqualTo("Resource created");
  }

  @Test
  @DisplayName("success(T data, String message) returns SingleResponse with HTTP 200 and data")
  void successWithDataAndMessage_returnsSingleResponse() {
    ResponseEntity<SingleResponse<String>> response =
        responseFactory.success("my-data", "Data fetched");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(200);
    assertThat(response.getBody().getMessage()).isEqualTo("Data fetched");
    assertThat(response.getBody().getData()).isEqualTo("my-data");
  }

  @Test
  @DisplayName("success(HttpStatus, T data, String message) returns SingleResponse with custom status")
  void successWithStatusDataAndMessage_returnsSingleResponse() {
    ResponseEntity<SingleResponse<Integer>> response =
        responseFactory.success(HttpStatus.CREATED, 42, "Item created");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(201);
    assertThat(response.getBody().getMessage()).isEqualTo("Item created");
    assertThat(response.getBody().getData()).isEqualTo(42);
  }

  @Test
  @DisplayName("successPage returns SingleResponse wrapping PageResponse without leaking PageImpl metadata")
  void successPage_returnsSingleResponseWithPageResponse() {
    List<String> items = List.of("item1", "item2");
    Page<String> page = new PageImpl<>(items, PageRequest.of(0, 10), 2);

    ResponseEntity<SingleResponse<PageResponse<String>>> response =
        responseFactory.successPage(page, "Page fetched");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(200);
    assertThat(response.getBody().getMessage()).isEqualTo("Page fetched");

    PageResponse<String> pageResponse = response.getBody().getData();
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getItems()).containsExactly("item1", "item2");
    assertThat(pageResponse.getPage()).isEqualTo(0);
    assertThat(pageResponse.getSize()).isEqualTo(10);
    assertThat(pageResponse.getTotalElements()).isEqualTo(2L);
    assertThat(pageResponse.getTotalPages()).isEqualTo(1);
    assertThat(pageResponse.isLast()).isTrue();
  }

  @Test
  @DisplayName("createErrorResponse sets httpStatus, errorCode, message, path, and timestamp")
  void createErrorResponse_buildsValidErrorResponse() {
    ErrorResponse error =
        responseFactory.createErrorResponse(
            HttpStatus.NOT_FOUND, "USER_101", "User not found", "/api/v1/users/123");

    assertThat(error.getHttpStatus()).isEqualTo(404);
    assertThat(error.getErrorCode()).isEqualTo("USER_101");
    assertThat(error.getMessage()).isEqualTo("User not found");
    assertThat(error.getPath()).isEqualTo("/api/v1/users/123");
    assertThat(error.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("createDetailedErrorResponse sets items map alongside ErrorResponse fields")
  void createDetailedErrorResponse_buildsValidDetailedErrorResponse() {
    Map<String, List<String>> items =
        Map.of(
            "email", List.of("Email is invalid", "Email must not be blank"),
            "password", List.of("Password too short"));

    DetailedErrorResponse error =
        responseFactory.createDetailedErrorResponse(
            HttpStatus.BAD_REQUEST,
            "WEB_400_VAL",
            "Validation failed",
            "/api/v1/auth/register",
            items);

    assertThat(error.getHttpStatus()).isEqualTo(400);
    assertThat(error.getErrorCode()).isEqualTo("WEB_400_VAL");
    assertThat(error.getMessage()).isEqualTo("Validation failed");
    assertThat(error.getPath()).isEqualTo("/api/v1/auth/register");
    assertThat(error.getTimestamp()).isNotNull();
    assertThat(error.getItems()).hasSize(2);
    assertThat(error.getItems().get("email"))
        .containsExactly("Email is invalid", "Email must not be blank");
  }
}
