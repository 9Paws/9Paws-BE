package com.ninepaws.backend.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.dtos.response.ResponseFactory;
import com.ninepaws.backend.dtos.response.base.DetailedErrorResponse;
import com.ninepaws.backend.dtos.response.base.ErrorResponse;
import com.ninepaws.backend.exceptions.error.SystemErrorCode;
import com.ninepaws.backend.exceptions.error.UserErrorCode;
import com.ninepaws.backend.exceptions.error.WebErrorCode;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    ResponseFactory responseFactory = new ResponseFactory();
    handler = new GlobalExceptionHandler(responseFactory);
    request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/test");
  }

  @Test
  @DisplayName("BaseAppException returns ErrorResponse with domain error code and HTTP status")
  void whenBaseAppException_returnsErrorResponse() {
    BaseAppException ex = new BaseAppException(UserErrorCode.USER_NOT_FOUND, "User 123 not found");

    ResponseEntity<ErrorResponse> response = handler.handleBaseAppException(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(404);
    assertThat(response.getBody().getErrorCode()).isEqualTo("USER_101");
    assertThat(response.getBody().getMessage()).isEqualTo("User 123 not found");
    assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    assertThat(response.getBody().getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("MethodArgumentNotValidException returns DetailedErrorResponse with grouped errors")
  void whenMethodArgumentNotValidException_returnsDetailedErrorResponse() {
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError error1 = new FieldError("object", "email", "must not be blank");
    FieldError error2 = new FieldError("object", "email", "must be a well-formed email address");
    FieldError error3 = new FieldError("object", "password", "must be at least 8 characters");
    ObjectError globalError = new ObjectError("dateRange", "End date must be after start date");

    when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2, error3));
    when(bindingResult.getGlobalErrors()).thenReturn(List.of(globalError));

    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

    ResponseEntity<DetailedErrorResponse> response = handler.handleValidationException(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getHttpStatus()).isEqualTo(400);
    assertThat(response.getBody().getErrorCode()).isEqualTo("WEB_400_VAL");
    assertThat(response.getBody().getItems()).hasSize(3);
    assertThat(response.getBody().getItems().get("email"))
        .containsExactly("must not be blank", "must be a well-formed email address");
    assertThat(response.getBody().getItems().get("password"))
        .containsExactly("must be at least 8 characters");
    assertThat(response.getBody().getItems().get("dateRange"))
        .containsExactly("End date must be after start date");
  }

  @Test
  @DisplayName("ConstraintViolationException returns DetailedErrorResponse")
  void whenConstraintViolationException_returnsDetailedErrorResponse() {
    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    Path path = mock(Path.class);
    when(path.toString()).thenReturn("pageNumber");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must be greater than or equal to 0");

    ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

    ResponseEntity<DetailedErrorResponse> response = handler.handleConstraintViolation(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode()).isEqualTo("WEB_400_VAL");
    assertThat(response.getBody().getItems().get("pageNumber"))
        .containsExactly("must be greater than or equal to 0");
  }

  @Test
  @DisplayName("EntityNotFoundException returns 404 ErrorResponse")
  void whenEntityNotFoundException_returns404() {
    EntityNotFoundException ex = new EntityNotFoundException("User entity missing");

    ResponseEntity<ErrorResponse> response = handler.handleEntityNotFound(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode()).isEqualTo(WebErrorCode.NOT_FOUND.getErrorCode());
  }

  @Test
  @DisplayName("DataIntegrityViolationException returns 400 ErrorResponse")
  void whenDataIntegrityViolationException_returns400() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("Unique index violation");

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode())
        .isEqualTo(WebErrorCode.BAD_REQUEST.getErrorCode());
  }

  @Test
  @DisplayName("BadCredentialsException returns 401 ErrorResponse")
  void whenBadCredentialsException_returns401() {
    BadCredentialsException ex = new BadCredentialsException("Bad credentials");

    ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode())
        .isEqualTo(WebErrorCode.UNAUTHORIZED.getErrorCode());
  }

  @Test
  @DisplayName("InsufficientAuthenticationException returns 401 ErrorResponse")
  void whenInsufficientAuthentication_returns401() {
    InsufficientAuthenticationException ex =
        new InsufficientAuthenticationException("Token required");

    ResponseEntity<ErrorResponse> response = handler.handleInsufficientAuth(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
  }

  @Test
  @DisplayName("AccessDeniedException returns 403 ErrorResponse")
  void whenAccessDeniedException_returns403() {
    AccessDeniedException ex = new AccessDeniedException("Forbidden action");

    ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode()).isEqualTo(WebErrorCode.FORBIDDEN.getErrorCode());
  }

  @Test
  @DisplayName("Transport exceptions return appropriate error response")
  void whenTransportException_returnsAppropriateErrorResponse() {
    HttpRequestMethodNotSupportedException methodEx =
        new HttpRequestMethodNotSupportedException("POST");
    ResponseEntity<ErrorResponse> methodRes = handler.handleMethodNotAllowed(methodEx, request);
    assertThat(methodRes.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

    NoResourceFoundException notFoundEx =
        new NoResourceFoundException(
            org.springframework.http.HttpMethod.GET, "/static/file.css", "Resource not found");
    ResponseEntity<ErrorResponse> notFoundRes = handler.handleNoResourceFound(notFoundEx, request);
    assertThat(notFoundRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Generic Exception returns 500 with safe SystemErrorCode")
  void whenGenericException_returns500WithSafeMessage() {
    Exception ex = new RuntimeException("DB credentials leaked in sensitive message");

    ResponseEntity<ErrorResponse> response = handler.handleUnknownException(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode())
        .isEqualTo(SystemErrorCode.INTERNAL_SERVER_ERROR.getErrorCode());
    assertThat(response.getBody().getMessage())
        .isEqualTo(SystemErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    assertThat(response.getBody().getMessage()).doesNotContain("DB credentials");
  }
}
