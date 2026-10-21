package com.ninepaws.backend.controllers;

import com.ninepaws.backend.dtos.response.ResponseFactory;
import com.ninepaws.backend.dtos.response.base.PageResponse;
import com.ninepaws.backend.dtos.response.base.SingleResponse;
import com.ninepaws.backend.dtos.response.base.SuccessResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
public abstract class AbstractBaseController {

  protected ResponseFactory responseFactory = new ResponseFactory();

  @Autowired
  public void setResponseFactory(ResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  protected <T> ResponseEntity<SingleResponse<T>> created(T data, String message) {
    return responseFactory.success(HttpStatus.CREATED, data, message);
  }

  protected ResponseEntity<SuccessResponse> created(String message) {
    return responseFactory.success(HttpStatus.CREATED, message);
  }

  protected <T> ResponseEntity<SingleResponse<T>> success(T data, String message) {
    return responseFactory.success(data, message);
  }

  protected ResponseEntity<SuccessResponse> success(String message) {
    return responseFactory.success(message);
  }

  protected <T> ResponseEntity<SingleResponse<PageResponse<T>>> successPage(
      Page<T> pageData, String message) {
    return responseFactory.successPage(pageData, message);
  }
}
