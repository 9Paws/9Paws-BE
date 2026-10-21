package com.ninepaws.backend.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninepaws.backend.dtos.response.ResponseFactory;
import com.ninepaws.backend.dtos.response.base.PageResponse;
import com.ninepaws.backend.dtos.response.base.SingleResponse;
import com.ninepaws.backend.dtos.response.base.SuccessResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AbstractBaseControllerTest {

  // Concrete test subclass to exercise protected methods of AbstractBaseController
  static class TestController extends AbstractBaseController {
    public ResponseEntity<SingleResponse<String>> testCreatedWithData(String data, String msg) {
      return created(data, msg);
    }

    public ResponseEntity<SuccessResponse> testCreatedWithoutData(String msg) {
      return created(msg);
    }

    public ResponseEntity<SingleResponse<String>> testSuccessWithData(String data, String msg) {
      return success(data, msg);
    }

    public ResponseEntity<SuccessResponse> testSuccessWithoutData(String msg) {
      return success(msg);
    }

    public ResponseEntity<SingleResponse<PageResponse<String>>> testSuccessPage(
        Page<String> page, String msg) {
      return successPage(page, msg);
    }
  }

  private TestController controller;

  @BeforeEach
  void setUp() {
    controller = new TestController();
    controller.setResponseFactory(new ResponseFactory());
  }

  @Test
  @DisplayName("created(T data, String message) returns 201 SingleResponse")
  void createdWithData_returnsCreatedSingleResponse() {
    ResponseEntity<SingleResponse<String>> res =
        controller.testCreatedWithData("created-data", "Item created");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().getHttpStatus()).isEqualTo(201);
    assertThat(res.getBody().getMessage()).isEqualTo("Item created");
    assertThat(res.getBody().getData()).isEqualTo("created-data");
  }

  @Test
  @DisplayName("created(String message) returns 201 SuccessResponse")
  void createdWithoutData_returnsCreatedSuccessResponse() {
    ResponseEntity<SuccessResponse> res = controller.testCreatedWithoutData("Item created");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().getHttpStatus()).isEqualTo(201);
    assertThat(res.getBody().getMessage()).isEqualTo("Item created");
  }

  @Test
  @DisplayName("success(T data, String message) returns 200 SingleResponse")
  void successWithData_returnsOkSingleResponse() {
    ResponseEntity<SingleResponse<String>> res =
        controller.testSuccessWithData("ok-data", "Item found");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().getHttpStatus()).isEqualTo(200);
    assertThat(res.getBody().getData()).isEqualTo("ok-data");
  }

  @Test
  @DisplayName("success(String message) returns 200 SuccessResponse")
  void successWithoutData_returnsOkSuccessResponse() {
    ResponseEntity<SuccessResponse> res = controller.testSuccessWithoutData("Action done");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().getHttpStatus()).isEqualTo(200);
    assertThat(res.getBody().getMessage()).isEqualTo("Action done");
  }

  @Test
  @DisplayName("successPage returns 200 SingleResponse wrapping PageResponse")
  void successPage_returnsOkPageResponse() {
    Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 10), 2);
    ResponseEntity<SingleResponse<PageResponse<String>>> res =
        controller.testSuccessPage(page, "Items found");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().getData().getItems()).containsExactly("a", "b");
  }
}
