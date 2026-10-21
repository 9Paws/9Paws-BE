package com.ninepaws.backend.exceptions;

import com.ninepaws.backend.dtos.response.ResponseFactory;
import com.ninepaws.backend.dtos.response.base.DetailedErrorResponse;
import com.ninepaws.backend.dtos.response.base.ErrorResponse;
import com.ninepaws.backend.exceptions.error.SystemErrorCode;
import com.ninepaws.backend.exceptions.error.WebErrorCode;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final ResponseFactory responseFactory;

  public GlobalExceptionHandler() {
    this(new ResponseFactory());
  }

  // 1. Business Logic Exceptions
  @ExceptionHandler(BaseAppException.class)
  public ResponseEntity<ErrorResponse> handleBaseAppException(
      BaseAppException e, HttpServletRequest request) {
    AppErrorCode errorCode = e.getErrorCode();

    if (errorCode.getHttpStatus().is4xxClientError()) {
      log.warn(
          "Business Exception [{}] at {}: {}",
          errorCode.getErrorCode(),
          request.getRequestURI(),
          e.getMessage());
    } else {
      log.error(
          "Internal Business Error [{}] at {}: {}",
          errorCode.getErrorCode(),
          request.getRequestURI(),
          e.getMessage(),
          e);
    }

    ErrorResponse response =
        responseFactory.createErrorResponse(
            errorCode.getHttpStatus(),
            errorCode.getErrorCode(),
            e.getMessage(),
            request.getRequestURI());
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }

  // 2. DTO Validation Exceptions (RequestBody, ModelAttribute)
  @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
  public ResponseEntity<DetailedErrorResponse> handleValidationException(
      Exception e, HttpServletRequest request) {
    log.warn("Validation failed at: {}", request.getRequestURI());
    Map<String, List<String>> errors = new HashMap<>();

    BindingResult bindingResult =
        (e instanceof MethodArgumentNotValidException manve)
            ? manve.getBindingResult()
            : (e instanceof BindException be) ? be.getBindingResult() : null;

    if (bindingResult != null) {
      for (FieldError fieldError : bindingResult.getFieldErrors()) {
        errors
            .computeIfAbsent(fieldError.getField(), k -> new ArrayList<>())
            .add(fieldError.getDefaultMessage());
      }
      for (ObjectError globalError : bindingResult.getGlobalErrors()) {
        errors
            .computeIfAbsent(globalError.getObjectName(), k -> new ArrayList<>())
            .add(globalError.getDefaultMessage());
      }
    }

    DetailedErrorResponse response =
        responseFactory.createDetailedErrorResponse(
            WebErrorCode.VALIDATION_FAILED.getHttpStatus(),
            WebErrorCode.VALIDATION_FAILED.getErrorCode(),
            WebErrorCode.VALIDATION_FAILED.getMessage(),
            request.getRequestURI(),
            errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  // 3. Param & PathVariable Validation Exceptions
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<DetailedErrorResponse> handleConstraintViolation(
      ConstraintViolationException e, HttpServletRequest request) {
    log.warn("Constraint violation at: {}", request.getRequestURI());
    Map<String, List<String>> errors = new HashMap<>();

    for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
      String propertyPath = violation.getPropertyPath().toString();
      errors.computeIfAbsent(propertyPath, k -> new ArrayList<>()).add(violation.getMessage());
    }

    DetailedErrorResponse response =
        responseFactory.createDetailedErrorResponse(
            WebErrorCode.VALIDATION_FAILED.getHttpStatus(),
            WebErrorCode.VALIDATION_FAILED.getErrorCode(),
            "Constraint violation error",
            request.getRequestURI(),
            errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  // Spring 7 Method Parameter Validation (using getParameterValidationResults)
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<DetailedErrorResponse> handleHandlerMethodValidation(
      HandlerMethodValidationException e, HttpServletRequest request) {
    log.warn("Handler method validation failed at: {}", request.getRequestURI());
    Map<String, List<String>> errors = new HashMap<>();

    e.getParameterValidationResults()
        .forEach(
            result -> {
              String paramName = result.getMethodParameter().getParameterName();
              List<String> messages =
                  result.getResolvableErrors().stream()
                      .map(org.springframework.context.MessageSourceResolvable::getDefaultMessage)
                      .filter(Objects::nonNull)
                      .toList();
              errors
                  .computeIfAbsent(
                      paramName != null ? paramName : "parameter", k -> new ArrayList<>())
                  .addAll(messages);
            });

    DetailedErrorResponse response =
        responseFactory.createDetailedErrorResponse(
            WebErrorCode.VALIDATION_FAILED.getHttpStatus(),
            WebErrorCode.VALIDATION_FAILED.getErrorCode(),
            "Request parameter validation failed",
            request.getRequestURI(),
            errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  // 4. Persistence / Entity Not Found Exception
  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleEntityNotFound(
      EntityNotFoundException e, HttpServletRequest request) {
    log.warn("Entity not found at {}: {}", request.getRequestURI(), e.getMessage());
    return buildFromAppErrorCode(WebErrorCode.NOT_FOUND, request.getRequestURI(), e.getMessage());
  }

  // 5. Database Constraint Conflict
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException e, HttpServletRequest request) {
    log.warn("Database conflict at {}: {}", request.getRequestURI(), e.getMessage());
    return buildFromAppErrorCode(
        WebErrorCode.BAD_REQUEST, request.getRequestURI(), "Data integrity violation or conflict");
  }

  // 6. Security Exceptions (Authentication & Authorization)
  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(
      BadCredentialsException e, HttpServletRequest request) {
    log.warn("Bad credentials at: {}", request.getRequestURI());
    String message =
        (e.getMessage() != null && !e.getMessage().isBlank())
            ? e.getMessage()
            : "Invalid email or password";
    return buildFromAppErrorCode(WebErrorCode.UNAUTHORIZED, request.getRequestURI(), message);
  }

  @ExceptionHandler(InsufficientAuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientAuth(
      InsufficientAuthenticationException e, HttpServletRequest request) {
    log.warn("Unauthorized access at: {}", request.getRequestURI());
    return buildFromAppErrorCode(WebErrorCode.UNAUTHORIZED, request.getRequestURI());
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthenticationException(
      AuthenticationException e, HttpServletRequest request) {
    log.warn("Authentication failed at: {}", request.getRequestURI());
    return buildFromAppErrorCode(
        WebErrorCode.UNAUTHORIZED, request.getRequestURI(), e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException e, HttpServletRequest request) {
    log.warn("Access denied at: {}", request.getRequestURI());
    return buildFromAppErrorCode(WebErrorCode.FORBIDDEN, request.getRequestURI());
  }

  // 7. Request Payload & Transport Errors
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
    log.warn("Method not allowed at {}: {}", request.getRequestURI(), e.getMessage());
    return buildFromAppErrorCode(WebErrorCode.METHOD_NOT_ALLOWED, request.getRequestURI());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
    log.warn("Media type not supported at {}: {}", request.getRequestURI(), e.getMessage());
    return buildFromAppErrorCode(WebErrorCode.UNSUPPORTED_MEDIA_TYPE, request.getRequestURI());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFound(
      NoResourceFoundException e, HttpServletRequest request) {
    return buildFromAppErrorCode(WebErrorCode.NOT_FOUND, request.getRequestURI());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSize(
      MaxUploadSizeExceededException e, HttpServletRequest request) {
    log.warn("File size exceeded at: {}", request.getRequestURI());
    return buildFromAppErrorCode(
        WebErrorCode.BAD_REQUEST, request.getRequestURI(), "Uploaded file exceeds maximum limit");
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingRequestHeaderException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorResponse> handleBadRequestTypes(
      Exception e, HttpServletRequest request) {
    log.warn("Malformed request at {}: {}", request.getRequestURI(), e.getMessage());
    return buildFromAppErrorCode(
        WebErrorCode.BAD_REQUEST,
        request.getRequestURI(),
        "Invalid request: Malformed or missing parameter");
  }

  // 8. Catch-all: Unexpected Unhandled Exceptions
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnknownException(
      Exception e, HttpServletRequest request) {
    log.error("Unhandled Server Exception at: {}", request.getRequestURI(), e);
    return buildFromAppErrorCode(SystemErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI());
  }

  private ResponseEntity<ErrorResponse> buildFromAppErrorCode(AppErrorCode errorCode, String path) {
    return buildFromAppErrorCode(errorCode, path, errorCode.getMessage());
  }

  private ResponseEntity<ErrorResponse> buildFromAppErrorCode(
      AppErrorCode errorCode, String path, String customMessage) {
    ErrorResponse response =
        responseFactory.createErrorResponse(
            errorCode.getHttpStatus(), errorCode.getErrorCode(), customMessage, path);
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }
}
