# Response Standardization & Centralized Error Handling Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize API responses and centralized exception handling for NinePaws backend using the Envelope Pattern, Domain-Driven Error Codes, and Spring Security Filter Chain exception bridging, fully adapted for Spring Boot 4.1.1 (Spring 7) and Java 25.

**Architecture:** 
- Response Envelope hierarchy based on `AbstractBaseResponse` (`SingleResponse<T>`, `SuccessResponse`, `PageResponse<T>`, `ErrorResponse`, `DetailedErrorResponse`) built via `ResponseFactory` and exposed to controllers through `AbstractBaseController`.
- Exception hierarchy centered around `BaseAppException` and `AppErrorCode` (with domain enums `WebErrorCode`, `SystemErrorCode`, `AuthErrorCode`, `UserErrorCode`), keeping existing exceptions backward-compatible.
- Spring Security 7 filter chain bridged to `GlobalExceptionHandler` (`@RestControllerAdvice`) via `HandlerExceptionResolver` in `CustomAuthenticationEntryPoint` and `CustomAccessDeniedHandler`.
- Global error handler covering 8 categories (business logic, bean validation, method parameter validation adjusted for Spring 7 `getParameterValidationResults()`, persistence, security, transport, file size, catch-all), with 4xx logged at WARN and 5xx logged at ERROR with stack trace.

**Tech Stack:** Java 25, Spring Boot 4.1.1 (Spring Framework 7.0.9, Spring Security 7.1.1), Jakarta Validation, Lombok (`@SuperBuilder`), Jackson, JUnit 5, Mockito, MockMvc.

**Spec:** `docs/Response-ErrorHandling-Architecture.md`

## Global Constraints

- Java version: 25 (`<java.version>25</java.version>`).
- Spring Boot version: 4.1.1 with Spring Framework 7.0.9.
- Method parameter validation: Use `e.getParameterValidationResults()` instead of deprecated/removed Spring 6 `getAllValidationResults()`.
- Package namespace: `com.ninepaws.backend`. Response DTOs in `dtos.response.base`, factory in `dtos.response`, exceptions in `exceptions` and `exceptions.error`, base controller in `controllers`, security in `security`.
- Immutability & DI: Controller subclasses use constructor injection; `AbstractBaseController` uses `@Autowired protected void setResponseFactory(...)` to avoid constructor pollution in subclasses.
- Clean Cutover: Migrate all callers of `ApiResponse<T>`, delete `ApiResponse.java`, and update all controller/integration tests to verify new envelope attributes (`httpStatus`, `message`, `data`, `errorCode`, `timestamp`, `path`, `items`).

## Review Focus

1. **Spring 7 Method Validation compatibility:** Validation on `@RequestParam` or `@PathVariable` throws `HandlerMethodValidationException` which in Spring 7 provides `getParameterValidationResults()` instead of Spring 6's `getAllValidationResults()`. Ensure `DetailedErrorResponse` maps parameter names and error messages cleanly without NPE or compilation failure.
2. **Spring Security Filter Chain error bridging:** Expired, forged, or missing JWT on secured endpoints must resolve through `CustomAuthenticationEntryPoint` and `CustomAccessDeniedHandler` to `GlobalExceptionHandler` returning `ErrorResponse` with HTTP 401/403 and `WEB_401`/`WEB_403` instead of hardcoded strings or container HTML.
3. **Validation error grouping without overwrite:** When multiple constraints fail on the same field (e.g. `@NotBlank` and `@Size`), `DetailedErrorResponse.items` (`Map<String, List<String>>`) must collect all violation messages into a list rather than overwriting with the last one.
4. **Log pollution prevention on 4xx errors:** 4xx client errors (bad credentials, validation failure, resource not found) must log at `WARN` without stack trace, while unhandled 5xx exceptions log at `ERROR` with full stack trace and return safe `SYS_500` message without leaking internal details.
5. **Clean migration from legacy `ApiResponse`:** All 11 auth endpoints in `AuthController` and their corresponding unit & integration tests must seamlessly transition to `SingleResponse<T>` and `SuccessResponse`, with no residual references to `ApiResponse`.

---

### Task 1: Response DTO Foundation & ResponseFactory

**Files:**
- Create: `src/main/java/com/ninepaws/backend/dtos/response/base/AbstractBaseResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/response/base/SingleResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/response/base/SuccessResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/response/base/PageResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/response/base/ErrorResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/response/base/DetailedErrorResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/response/ResponseFactory.java`
- Test: `src/test/java/com/ninepaws/backend/dtos/response/ResponseFactoryTest.java`

**Interfaces:**
- Produces:
  - `AbstractBaseResponse` (`int httpStatus`, `String message`)
  - `SingleResponse<T>` (`T data`)
  - `SuccessResponse`
  - `PageResponse<T>` (`List<T> items`, `int page`, `int size`, `long totalElements`, `int totalPages`, `boolean last`, `static <T> PageResponse<T> from(Page<T>)`)
  - `ErrorResponse` (`String errorCode`, `Instant timestamp`, `String path`)
  - `DetailedErrorResponse` (`Map<String, List<String>> items`)
  - `ResponseFactory` methods: `success(HttpStatus, String)`, `success(String)`, `success(HttpStatus, T, String)`, `success(T, String)`, `successPage(Page<T>, String)`, `createErrorResponse(HttpStatus, String, String, String)`, `createDetailedErrorResponse(HttpStatus, String, String, String, Map<String, List<String>>)`

- [ ] **Step 1: Write the unit test `ResponseFactoryTest.java`**

Verify that `ResponseFactory` builds `SuccessResponse`, `SingleResponse`, `PageResponse`, `ErrorResponse`, and `DetailedErrorResponse` with correct status codes, timestamps, and serialized field names.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ResponseFactoryTest`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement Response DTOs and `ResponseFactory`**

Implement classes in `com.ninepaws.backend.dtos.response.base` and `com.ninepaws.backend.dtos.response` with `@Getter`, `@Setter`, `@SuperBuilder`, `@NoArgsConstructor`, `@AllArgsConstructor`, and `@JsonPropertyOrder`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=ResponseFactoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/dtos/response/ src/test/java/com/ninepaws/backend/dtos/response/
git commit -m "feat(response): add standardized response DTOs and ResponseFactory"
```

---

### Task 2: Error Code Architecture & Base Exceptions

**Files:**
- Create: `src/main/java/com/ninepaws/backend/exceptions/AppErrorCode.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/error/WebErrorCode.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/error/SystemErrorCode.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/error/AuthErrorCode.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/error/UserErrorCode.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/BaseAppException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/AppException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/InvalidOtpException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/RateLimitException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/ResourceNotFoundException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/DuplicateResourceException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/TokenRevokedException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/AccountNotVerifiedException.java`
- Modify: `src/main/java/com/ninepaws/backend/exceptions/InvalidPasswordException.java`
- Test: `src/test/java/com/ninepaws/backend/exceptions/BaseAppExceptionTest.java`

**Interfaces:**
- Consumes: `AppErrorCode`
- Produces:
  - `AppErrorCode` interface: `String getErrorCode()`, `String getMessage()`, `HttpStatus getHttpStatus()`
  - `WebErrorCode` (`BAD_REQUEST`, `VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `TOO_MANY_REQUESTS`, `INTERNAL_SERVER_ERROR`)
  - `SystemErrorCode` (`INTERNAL_SERVER_ERROR`, `DATABASE_ERROR`, `EXTERNAL_SERVICE_ERROR`)
  - `AuthErrorCode` (`INVALID_CREDENTIALS`, `INVALID_OTP`, `ACCOUNT_NOT_VERIFIED`, `TOKEN_REVOKED`, `INVALID_PASSWORD`, `RATE_LIMIT_EXCEEDED`)
  - `UserErrorCode` (`USER_NOT_FOUND`, `USER_ALREADY_EXISTS`)
  - `BaseAppException`: constructors for `(AppErrorCode)`, `(AppErrorCode, String customMessage)`, `(AppErrorCode, Object... args)` with safe string formatting.
  - Subclasses (`InvalidOtpException`, etc.) extending `BaseAppException` with appropriate domain error codes while preserving message overrides.

- [ ] **Step 1: Write test `BaseAppExceptionTest.java`**

Test `BaseAppException` message formatting with and without `%s` parameters, error code preservation, and subclass instantiation.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=BaseAppExceptionTest`
Expected: FAIL

- [ ] **Step 3: Implement `AppErrorCode`, domain error code enums, `BaseAppException`, and refactor existing exceptions**

Implement `AppErrorCode`, `WebErrorCode`, `SystemErrorCode`, `AuthErrorCode`, `UserErrorCode`, `BaseAppException`. Update `AppException` to extend `BaseAppException` and bridge status/code. Update specific exceptions (`InvalidOtpException`, etc.) to extend `BaseAppException` with default error codes.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=BaseAppExceptionTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/exceptions/ src/test/java/com/ninepaws/backend/exceptions/BaseAppExceptionTest.java
git commit -m "feat(exception): implement AppErrorCode hierarchy and BaseAppException"
```

---

### Task 3: Spring Security Filter Chain Exception Bridging

**Files:**
- Create: `src/main/java/com/ninepaws/backend/security/CustomAuthenticationEntryPoint.java`
- Create: `src/main/java/com/ninepaws/backend/security/CustomAccessDeniedHandler.java`
- Modify: `src/main/java/com/ninepaws/backend/security/SecurityConfig.java:60-106`
- Test: `src/test/java/com/ninepaws/backend/security/SecurityExceptionHandlersTest.java`

**Interfaces:**
- Consumes: `HandlerExceptionResolver` (`@Qualifier("handlerExceptionResolver")`)
- Produces:
  - `CustomAuthenticationEntryPoint implements AuthenticationEntryPoint`: delegates `authException` to `resolver.resolveException(request, response, null, authException)`
  - `CustomAccessDeniedHandler implements AccessDeniedHandler`: delegates `accessDeniedException` to `resolver.resolveException(request, response, null, accessDeniedException)`
  - `SecurityConfig` registered with `.exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint).accessDeniedHandler(accessDeniedHandler))`

- [ ] **Step 1: Write test `SecurityExceptionHandlersTest.java`**

Verify that `CustomAuthenticationEntryPoint` and `CustomAccessDeniedHandler` invoke `resolver.resolveException(request, response, null, exception)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SecurityExceptionHandlersTest`
Expected: FAIL

- [ ] **Step 3: Implement `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`, and wire into `SecurityConfig`**

Implement components with constructor injection of `@Qualifier("handlerExceptionResolver") HandlerExceptionResolver`. In `SecurityConfig`, replace the inline hardcoded lambda in `.authenticationEntryPoint(...)` with injected `CustomAuthenticationEntryPoint` and add `.accessDeniedHandler(customAccessDeniedHandler)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SecurityExceptionHandlersTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/security/ src/test/java/com/ninepaws/backend/security/SecurityExceptionHandlersTest.java
git commit -m "feat(security): add CustomAuthenticationEntryPoint and CustomAccessDeniedHandler"
```

---

### Task 4: Global Exception Handler with Spring 7 Adaptation

**Files:**
- Modify: `src/main/java/com/ninepaws/backend/exceptions/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/ninepaws/backend/exceptions/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ResponseFactory`, `BaseAppException`, `WebErrorCode`, `SystemErrorCode`, Spring 7 `HandlerMethodValidationException.getParameterValidationResults()`
- Produces:
  - `@RestControllerAdvice GlobalExceptionHandler` handling:
    1. `BaseAppException` (and all subclasses) -> `ErrorResponse` (WARN for 4xx, ERROR with stack trace for 5xx)
    2. Bean Validation: `MethodArgumentNotValidException`, `BindException` -> `DetailedErrorResponse` with `Map<String, List<String>> items` including field & global errors
    3. Parameter Validation: `ConstraintViolationException` and `HandlerMethodValidationException` (Spring 7 `getParameterValidationResults()`) -> `DetailedErrorResponse`
    4. Persistence: `EntityNotFoundException` -> 404 `ErrorResponse`, `DataIntegrityViolationException` -> 400 `ErrorResponse`
    5. Security: `BadCredentialsException`, `InsufficientAuthenticationException`, `AuthenticationException`, `AccessDeniedException` -> 401 / 403 `ErrorResponse`
    6. HTTP Transport: `HttpRequestMethodNotSupportedException`, `HttpMessageNotReadableException`, `HttpMediaTypeNotSupportedException`, `MissingRequestHeaderException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`, `MaxUploadSizeExceededException`, `NoResourceFoundException` -> 400 / 404 / 405 / 415 `ErrorResponse`
    7. Catch-all: `Exception` -> 500 `SystemErrorCode.INTERNAL_SERVER_ERROR` with stack trace logged at ERROR

- [ ] **Step 1: Update unit test `GlobalExceptionHandlerTest.java`**

Update `GlobalExceptionHandlerTest` to assert against `ErrorResponse` and `DetailedErrorResponse` (`httpStatus`, `message`, `errorCode`, `timestamp`, `path`, `items`) for business exceptions, validation errors, security errors, and uncaught exceptions.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL

- [ ] **Step 3: Refactor `GlobalExceptionHandler.java`**

Implement comprehensive centralized error handling according to the architecture specification, using `e.getParameterValidationResults()` for Spring 7 compatibility.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/exceptions/GlobalExceptionHandler.java src/test/java/com/ninepaws/backend/exceptions/GlobalExceptionHandlerTest.java
git commit -m "feat(exception): implement centralized GlobalExceptionHandler with Spring 7 tuning"
```

---

### Task 5: Base Controller & AuthController Migration

**Files:**
- Create: `src/main/java/com/ninepaws/backend/controllers/AbstractBaseController.java`
- Modify: `src/main/java/com/ninepaws/backend/controllers/AuthController.java`
- Delete: `src/main/java/com/ninepaws/backend/dtos/common/ApiResponse.java`
- Modify: `src/test/java/com/ninepaws/backend/dtos/auth/AuthDtoValidationTest.java`

**Interfaces:**
- Consumes: `ResponseFactory`, `SingleResponse<T>`, `SuccessResponse`, `PageResponse<T>`
- Produces:
  - `AbstractBaseController` providing helper methods:
    - `created(T data, String message)` -> `ResponseEntity<SingleResponse<T>>` (201)
    - `created(String message)` -> `ResponseEntity<SuccessResponse>` (201)
    - `success(T data, String message)` -> `ResponseEntity<SingleResponse<T>>` (200)
    - `success(String message)` -> `ResponseEntity<SuccessResponse>` (200)
    - `successPage(Page<T>, String message)` -> `ResponseEntity<SingleResponse<PageResponse<T>>>` (200)
  - `AuthController extends AbstractBaseController`: returns `SingleResponse<AuthResponse>`, `SingleResponse<VerifyResetPasswordOtpResponse>`, or `SuccessResponse`.
  - Full removal of obsolete `ApiResponse.java`.

- [ ] **Step 1: Write test for `AbstractBaseController`**

Create `src/test/java/com/ninepaws/backend/controllers/AbstractBaseControllerTest.java` verifying helper methods produce correct status codes and body envelopes.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AbstractBaseControllerTest`
Expected: FAIL

- [ ] **Step 3: Implement `AbstractBaseController`, refactor `AuthController`, and remove `ApiResponse.java`**

Implement `AbstractBaseController` with `@Autowired protected void setResponseFactory(...)`. Refactor `AuthController` to extend `AbstractBaseController` and return `SingleResponse` / `SuccessResponse`. Remove `ApiResponse.java` and clean up `AuthDtoValidationTest.java`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AbstractBaseControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/controllers/ src/main/java/com/ninepaws/backend/dtos/common/ src/test/java/com/ninepaws/backend/
git commit -m "refactor(controllers): migrate AuthController to AbstractBaseController and remove ApiResponse"
```

---

### Task 6: Test Suite Updates & End-to-End Verification

**Files:**
- Modify: `src/test/java/com/ninepaws/backend/controllers/AuthControllerTest.java`
- Modify: `src/test/java/com/ninepaws/backend/integration/AuthIntegrationTest.java`

**Interfaces:**
- Consumes: Updated API endpoints and error envelopes
- Produces: Green test suite for all controller and integration tests asserting `httpStatus`, `message`, `data`, and `errorCode`.

- [ ] **Step 1: Update assertions in `AuthControllerTest.java`**

Replace `jsonPath("$.success")` and `jsonPath("$.errors")` with `jsonPath("$.httpStatus")`, `jsonPath("$.message")`, `jsonPath("$.data")`, `jsonPath("$.items")`, and `jsonPath("$.errorCode")`.

- [ ] **Step 2: Run `AuthControllerTest` to verify it passes**

Run: `./mvnw test -Dtest=AuthControllerTest`
Expected: PASS

- [ ] **Step 3: Update assertions in `AuthIntegrationTest.java`**

Update integration test scenarios (register, verify, login, refresh, logout, password change, unauthenticated access, rate limiting) to verify the new response envelopes and error codes.

- [ ] **Step 4: Run full project test suite**

Run: `./mvnw test`
Expected: All 184+ tests pass (BUILD SUCCESS).

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/ninepaws/backend/controllers/AuthControllerTest.java src/test/java/com/ninepaws/backend/integration/AuthIntegrationTest.java
git commit -m "test(auth): update AuthControllerTest and AuthIntegrationTest to verify new response envelopes"
```
