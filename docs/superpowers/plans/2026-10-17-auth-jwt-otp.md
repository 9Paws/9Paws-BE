# Authentication, JWT & Redis OTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement production-ready local authentication with stateless JWT (access + refresh tokens), Redis-backed token revocation/blacklist, 6-digit OTP verification via Gmail SMTP, forgot password, and change password flows for NinePaws Backend.

**Architecture:** Stateless REST architecture with Spring Security filter chain. Passwords hashed with BCrypt(12). JJWT 0.12.6 issues signed HMAC-SHA256 access tokens (30m) and refresh tokens (7d). Redis stores 6-digit OTPs with strict 120s TTL and handles token revocation via an unexpired access-token JTI blacklist and refresh-token rotation store.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Security, Spring Data JPA, PostgreSQL, Spring Data Redis (`StringRedisTemplate`), JJWT 0.12.6, Spring Mail (`JavaMailSender`), Jakarta Validation, Lombok.

**Spec:** `docs/superpowers/specs/2026-10-17-auth-jwt-otp-design.md`

## Global Constraints

- Secret key MUST be Base64-encoded and at least 256 bits (32 bytes) for HMAC-SHA256 (`Keys.hmacShaKeyFor`).
- Subject (`sub`) claim stores the user's UUID string (`userId.toString()`).
- All JJWT operations MUST use JJWT 0.12.6 modern fluent API (`Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`). Deprecated 0.11.x methods are forbidden.
- Registration and password reset OTPs MUST be 6-digit random strings generated via `java.security.SecureRandom`.
- OTPs MUST be stored in Redis with strict 120-second TTL and NEVER stored in PostgreSQL.
- OTPs MUST be deleted immediately upon successful verification to prevent replay attacks.
- Request and Response DTOs MUST be immutable Java `record` types with Jakarta Validation annotations.
- Controllers MUST be thin (no direct repository injection; map through DTOs).
- All passwords hashed using `BCryptPasswordEncoder(12)`.
- Global error response MUST wrap errors in standard `ApiResponse<T>` envelope without leaking stack traces or sensitive data.

## Review Focus

1. **OTP Resend Cooldown**: Attempting to resend OTP before the 60-second cooldown expires returns `429 Too Many Requests`. Pinned to Task 4 & Task 10.
2. **Access Token Blacklist Enforcement**: Requesting a protected endpoint with an access token whose `jti` is blacklisted returns `401 Unauthorized`. Pinned to Task 7 & Task 9.
3. **Refresh Token Single-Use (Rotation)**: Re-submitting an already used refresh token returns `401 Unauthorized` and does not issue new tokens. Pinned to Task 7 & Task 10.
4. **Password Reset Invalidates Active Sessions**: Successful password reset deletes all active refresh tokens for the user in Redis, immediately locking out existing sessions. Pinned to Task 7 & Task 10.
5. **Unverified Account Login Block**: Attempting to log in with valid credentials when `emailVerified == false` returns `403 Forbidden` with error code `ACCOUNT_NOT_VERIFIED`. Pinned to Task 10.

---

### Task 1: Core DTOs & Standard Response Envelope

**Files:**
- Create: `src/main/java/com/ninepaws/backend/dtos/common/ApiResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/RegisterRequest.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/LoginRequest.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/AuthResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/UserResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/VerifyOtpRequest.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/ResendOtpRequest.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/RefreshTokenRequest.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/ForgotPasswordRequest.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/VerifyResetPasswordOtpResponse.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/ResetPasswordRequest.java`
- Create: `src/main/java/com/ninepaws/backend/dtos/auth/ChangePasswordRequest.java`
- Create: `src/main/java/com/ninepaws/backend/enums/OtpType.java`
- Test: `src/test/java/com/ninepaws/backend/dtos/auth/AuthDtoValidationTest.java`

**Interfaces:**
- Produces:
  - `ApiResponse<T>(boolean success, String message, T data, Map<String, String> errors, Instant timestamp)`
  - `RegisterRequest(@NotBlank @Size(max=100) String fullName, @NotBlank @Email String email, @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$") String password)`
  - `LoginRequest(@NotBlank @Email String email, @NotBlank String password)`
  - `AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user)`
  - `UserResponse(UUID id, String fullName, String email, String phoneNumber, String avatarUrl, boolean emailVerified, String currency, String language)`
  - `VerifyOtpRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp="^\\d{6}$") String otp)`
  - `ResendOtpRequest(@NotBlank @Email String email, @NotNull OtpType type)`
  - `RefreshTokenRequest(@NotBlank String refreshToken)`
  - `ForgotPasswordRequest(@NotBlank @Email String email)`
  - `VerifyResetPasswordOtpResponse(String email, String resetToken)`
  - `ResetPasswordRequest(@NotBlank @Email String email, @NotBlank String resetToken, @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$") String newPassword)`
  - `ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$") String newPassword, @NotBlank String confirmPassword)`
  - `enum OtpType { REGISTER, FORGOT_PASSWORD }`

- [ ] **Step 1: Write failing validation unit tests in `AuthDtoValidationTest.java`**

```java
@Test
void whenRegisterRequestInvalidEmail_thenValidationFails() {
    RegisterRequest request = new RegisterRequest("Test User", "invalid-email", "Password123@");
    Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
}

@Test
void whenRegisterRequestWeakPassword_thenValidationFails() {
    RegisterRequest request = new RegisterRequest("Test User", "user@example.com", "weak");
    Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
}

@Test
void whenVerifyOtpRequestInvalidLength_thenValidationFails() {
    VerifyOtpRequest request = new VerifyOtpRequest("user@example.com", "123");
    Set<ConstraintViolation<VerifyOtpRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("otp"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AuthDtoValidationTest`
Expected: FAIL (classes not found)

- [ ] **Step 3: Implement records in `dtos/auth/`, `dtos/common/`, and `enums/OtpType.java`**

Implement Java record classes with Jakarta Validation annotations (`@NotBlank`, `@Email`, `@Size`, `@Pattern`, `@NotNull`). Implement `ApiResponse<T>` with static helper methods `success(String message, T data)` and `error(String message, Map<String, String> errors)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AuthDtoValidationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/dtos/ src/main/java/com/ninepaws/backend/enums/OtpType.java src/test/java/com/ninepaws/backend/dtos/
git commit -m "feat(auth): add auth request response DTOs and ApiResponse envelope"
```

---

### Task 2: Centralized Exceptions & GlobalExceptionHandler

**Files:**
- Create: `src/main/java/com/ninepaws/backend/exceptions/AppException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/ResourceNotFoundException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/DuplicateResourceException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/InvalidOtpException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/TokenRevokedException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/AccountNotVerifiedException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/RateLimitException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/InvalidPasswordException.java`
- Create: `src/main/java/com/ninepaws/backend/exceptions/GlobalExceptionHandler.java`
- Test: `src/test/java/com/ninepaws/backend/exceptions/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ApiResponse<T>`
- Produces:
  - `AppException(String message, HttpStatus status)`
  - `InvalidOtpException(String message)` -> 400 Bad Request
  - `DuplicateResourceException(String message)` -> 409 Conflict
  - `ResourceNotFoundException(String message)` -> 404 Not Found
  - `TokenRevokedException(String message)` -> 401 Unauthorized
  - `AccountNotVerifiedException(String message)` -> 403 Forbidden
  - `RateLimitException(String message)` -> 429 Too Many Requests
  - `GlobalExceptionHandler` with `@ExceptionHandler` for:
    - `MethodArgumentNotValidException` -> 400 with field errors map
    - `BadCredentialsException` -> 401 Unauthorized
    - `AccessDeniedException` -> 403 Forbidden
    - `AppException` and domain subclasses -> mapped `HttpStatus`
    - `Exception` -> 500 Internal Server Error (generic message, logs exception via `@Slf4j`)

- [ ] **Step 1: Write failing tests for GlobalExceptionHandler in `GlobalExceptionHandlerTest.java`**

```java
@Test
void whenMethodArgumentNotValidException_returns400WithErrorsMap() {
    // Arrange mock MethodArgumentNotValidException with field error "email": "must be a well-formed email address"
    // Act handler.handleValidationExceptions(ex)
    // Assert response status 400, body.success() == false, body.errors().containsKey("email")
}

@Test
void whenInvalidOtpException_returns400WithErrorMessage() {
    ResponseEntity<ApiResponse<Void>> res = handler.handleInvalidOtpException(new InvalidOtpException("Mã OTP không chính xác"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().message()).isEqualTo("Mã OTP không chính xác");
}

@Test
void whenRateLimitException_returns429() {
    ResponseEntity<ApiResponse<Void>> res = handler.handleRateLimitException(new RateLimitException("Vui lòng đợi 60 giây"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL

- [ ] **Step 3: Implement exception classes and `GlobalExceptionHandler`**

Create `AppException` extending `RuntimeException` with `HttpStatus`. Create domain subclasses. Implement `GlobalExceptionHandler` annotated with `@RestControllerAdvice`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/exceptions/ src/test/java/com/ninepaws/backend/exceptions/
git commit -m "feat(exception): implement domain exceptions and GlobalExceptionHandler"
```

---

### Task 3: Redis Configuration & Constants

**Files:**
- Create: `src/main/java/com/ninepaws/backend/constants/RedisKeyConstants.java`
- Create: `src/main/java/com/ninepaws/backend/configs/RedisConfig.java`
- Test: `src/test/java/com/ninepaws/backend/configs/RedisKeyConstantsTest.java`

**Interfaces:**
- Produces:
  - `RedisKeyConstants`:
    - `otpKey(OtpType type, String email) -> "9paws:otp:" + type + ":" + email`
    - `otpCooldownKey(OtpType type, String email) -> "9paws:otp:cooldown:" + type + ":" + email`
    - `refreshTokenKey(UUID userId, String jti) -> "9paws:refresh_token:" + userId + ":" + jti`
    - `userSessionsKey(UUID userId) -> "9paws:user_sessions:" + userId`
    - `tokenBlacklistKey(String jti) -> "9paws:token:blacklist:" + jti`
    - `passwordResetTokenKey(String email) -> "9paws:password_reset_token:" + email`
  - `RedisConfig`: Bean `StringRedisTemplate` with `StringRedisSerializer` for keys and values.

- [ ] **Step 1: Write test in `RedisKeyConstantsTest.java`**

```java
@Test
void shouldGenerateConsistentRedisKeys() {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    assertThat(RedisKeyConstants.otpKey(OtpType.REGISTER, "test@example.com"))
        .isEqualTo("9paws:otp:REGISTER:test@example.com");
    assertThat(RedisKeyConstants.refreshTokenKey(userId, "token-jti-123"))
        .isEqualTo("9paws:refresh_token:11111111-1111-1111-1111-111111111111:token-jti-123");
    assertThat(RedisKeyConstants.tokenBlacklistKey("token-jti-123"))
        .isEqualTo("9paws:token:blacklist:token-jti-123");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=RedisKeyConstantsTest`
Expected: FAIL

- [ ] **Step 3: Implement `RedisKeyConstants.java` and `RedisConfig.java`**

Implement key generation helper methods and configure `StringRedisTemplate` in `com.ninepaws.backend.configs.RedisConfig`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=RedisKeyConstantsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/constants/RedisKeyConstants.java src/main/java/com/ninepaws/backend/configs/RedisConfig.java src/test/java/com/ninepaws/backend/configs/
git commit -m "feat(redis): add RedisKeyConstants and RedisConfig"
```

---

### Task 4: OTP Service with SecureRandom & Redis TTL

**Files:**
- Create: `src/main/java/com/ninepaws/backend/services/OtpService.java`
- Create: `src/main/java/com/ninepaws/backend/services/impl/OtpServiceImpl.java`
- Test: `src/test/java/com/ninepaws/backend/services/OtpServiceTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate`, `RedisKeyConstants`, `OtpType`
- Produces:
  - `String generateAndStoreOtp(String email, OtpType type)`
  - `boolean verifyOtp(String email, OtpType type, String otp)`
  - `void checkCooldown(String email, OtpType type)`
  - `String generateAndStorePasswordResetToken(String email)`
  - `boolean verifyPasswordResetToken(String email, String token)`

- [ ] **Step 1: Write failing unit tests in `OtpServiceTest.java`**

```java
@Test
void generateAndStoreOtp_stores6DigitNumberWith120sTTL() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String otp = otpService.generateAndStoreOtp("user@example.com", OtpType.REGISTER);
    assertThat(otp).matches("^\\d{6}$");
    verify(valueOperations).set(eq("9paws:otp:REGISTER:user@example.com"), eq(otp), eq(Duration.ofSeconds(120)));
    verify(valueOperations).set(eq("9paws:otp:cooldown:REGISTER:user@example.com"), eq("SENT"), eq(Duration.ofSeconds(60)));
}

@Test
void checkCooldown_whenCooldownActive_throwsRateLimitException() {
    when(redisTemplate.hasKey("9paws:otp:cooldown:REGISTER:user@example.com")).thenReturn(true);
    assertThatThrownBy(() -> otpService.checkCooldown("user@example.com", OtpType.REGISTER))
        .isInstanceOf(RateLimitException.class);
}

@Test
void verifyOtp_whenValid_deletesOtpAndReturnsTrue() {
    when(redisTemplate.opsForValue().get("9paws:otp:REGISTER:user@example.com")).thenReturn("123456");
    boolean result = otpService.verifyOtp("user@example.com", OtpType.REGISTER, "123456");
    assertThat(result).isTrue();
    verify(redisTemplate).delete("9paws:otp:REGISTER:user@example.com");
}

@Test
void verifyOtp_whenInvalid_returnsFalseWithoutDeletingKey() {
    when(redisTemplate.opsForValue().get("9paws:otp:REGISTER:user@example.com")).thenReturn("123456");
    boolean result = otpService.verifyOtp("user@example.com", OtpType.REGISTER, "654321");
    assertThat(result).isFalse();
    verify(redisTemplate, never()).delete("9paws:otp:REGISTER:user@example.com");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=OtpServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement `OtpService` and `OtpServiceImpl`**

Use `SecureRandom.getInstanceStrong()` (fallback to `new SecureRandom()`) to generate 6-digit numbers (`String.format("%06d", number)`).
Set Redis key with 120s TTL and cooldown with 60s TTL.
Implement reset token using `UUID.randomUUID().toString()`, storing in Redis with 10-minute TTL.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=OtpServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/services/OtpService.java src/main/java/com/ninepaws/backend/services/impl/OtpServiceImpl.java src/test/java/com/ninepaws/backend/services/OtpServiceTest.java
git commit -m "feat(auth): implement OtpService with SecureRandom and Redis TTL"
```

---

### Task 5: Email Service with Gmail SMTP

**Files:**
- Create: `src/main/java/com/ninepaws/backend/services/EmailService.java`
- Create: `src/main/java/com/ninepaws/backend/services/impl/EmailServiceImpl.java`
- Test: `src/test/java/com/ninepaws/backend/services/EmailServiceTest.java`

**Interfaces:**
- Consumes: `JavaMailSender`
- Produces:
  - `void sendRegistrationOtp(String toEmail, String fullName, String otp)`
  - `void sendForgotPasswordOtp(String toEmail, String fullName, String otp)`

- [ ] **Step 1: Write failing unit tests in `EmailServiceTest.java`**

```java
@Test
void sendRegistrationOtp_sendsMimeMessageWithOtpContent() throws Exception {
    MimeMessage mimeMessage = new MimeMessage((Session) null);
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    emailService.sendRegistrationOtp("user@example.com", "Nguyen Van A", "123456");

    verify(mailSender).send(mimeMessage);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=EmailServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement `EmailService` and `EmailServiceImpl`**

Use `MimeMessageHelper(mimeMessage, true, "UTF-8")` to create clean HTML emails containing NinePaws branding, recipient greeting, 6-digit OTP code in bold, 2-minute expiration note, and security disclaimer.
Mark methods with `@Async` for non-blocking execution.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=EmailServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/services/EmailService.java src/main/java/com/ninepaws/backend/services/impl/EmailServiceImpl.java src/test/java/com/ninepaws/backend/services/EmailServiceTest.java
git commit -m "feat(mail): implement EmailService for OTP delivery"
```

---

### Task 6: JWT Service (JJWT 0.12.6) for Access & Refresh Tokens

**Files:**
- Create: `src/main/java/com/ninepaws/backend/security/JwtService.java`
- Create: `src/main/java/com/ninepaws/backend/security/TokenPayload.java`
- Test: `src/test/java/com/ninepaws/backend/security/JwtServiceTest.java`

**Interfaces:**
- Consumes:
  - `@Value("${app.jwt.secret}") String secretBase64`
  - `@Value("${app.jwt.access-token-expiration:1800000}") long accessTokenExpirationMs`
  - `@Value("${app.jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs`
- Produces:
  - `record TokenPayload(UUID userId, String email, String jti, String tokenType, Instant issuedAt, Instant expiresAt)`
  - `String generateAccessToken(UUID userId, String email, String jti)`
  - `String generateRefreshToken(UUID userId, String jti)`
  - `TokenPayload parseAndValidateToken(String token)`
  - `long getRemainingExpirationMs(String token)`
  - `long getAccessTokenExpirationSeconds()`
  - `long getRefreshTokenExpirationSeconds()`

- [ ] **Step 1: Write failing unit tests in `JwtServiceTest.java`**

```java
@Test
void generateAndParseAccessToken_returnsCorrectClaims() {
    UUID userId = UUID.randomUUID();
    String jti = UUID.randomUUID().toString();
    String token = jwtService.generateAccessToken(userId, "user@example.com", jti);

    TokenPayload payload = jwtService.parseAndValidateToken(token);
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.email()).isEqualTo("user@example.com");
    assertThat(payload.jti()).isEqualTo(jti);
    assertThat(payload.tokenType()).isEqualTo("ACCESS");
}

@Test
void generateAndParseRefreshToken_returnsCorrectClaims() {
    UUID userId = UUID.randomUUID();
    String jti = UUID.randomUUID().toString();
    String token = jwtService.generateRefreshToken(userId, jti);

    TokenPayload payload = jwtService.parseAndValidateToken(token);
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.jti()).isEqualTo(jti);
    assertThat(payload.tokenType()).isEqualTo("REFRESH");
}

@Test
void whenTokenTampered_parseThrowsJwtException() {
    String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@example.com", "jti-1");
    String tampered = token + "xyz";
    assertThatThrownBy(() -> jwtService.parseAndValidateToken(tampered))
        .isInstanceOf(JwtException.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=JwtServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement `JwtService` using JJWT 0.12.6**

Initialize HMAC key using `Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64))`.
Validate secret length >= 32 bytes (256 bits).
Generate tokens using `Jwts.builder().subject(...).claim("jti", ...).claim("type", ...).signWith(signingKey).compact()`.
Validate using `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=JwtServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/security/JwtService.java src/main/java/com/ninepaws/backend/security/TokenPayload.java src/test/java/com/ninepaws/backend/security/JwtServiceTest.java
git commit -m "feat(security): implement JJWT 0.12.6 JwtService"
```

---

### Task 7: Token Revocation Service (Redis Refresh Store & Blacklist)

**Files:**
- Create: `src/main/java/com/ninepaws/backend/services/TokenRevocationService.java`
- Create: `src/main/java/com/ninepaws/backend/services/impl/TokenRevocationServiceImpl.java`
- Test: `src/test/java/com/ninepaws/backend/services/TokenRevocationServiceTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate`, `RedisKeyConstants`, `JwtService`
- Produces:
  - `void recordRefreshToken(UUID userId, String jti, String token, long ttlSeconds)`
  - `boolean isRefreshTokenValid(UUID userId, String jti)`
  - `void revokeRefreshToken(UUID userId, String jti)`
  - `void blacklistAccessToken(String jti, long remainingExpirationMs)`
  - `boolean isAccessTokenBlacklisted(String jti)`
  - `void revokeAllUserSessions(UUID userId)`

- [ ] **Step 1: Write failing unit tests in `TokenRevocationServiceTest.java`**

```java
@Test
void recordRefreshToken_storesTokenAndAddsToUserSessionsSet() {
    UUID userId = UUID.randomUUID();
    tokenRevocationService.recordRefreshToken(userId, "jti-1", "token-value", 604800L);
    verify(valueOperations).set(eq("9paws:refresh_token:" + userId + ":jti-1"), eq("token-value"), eq(Duration.ofSeconds(604800L)));
    verify(setOperations).add(eq("9paws:user_sessions:" + userId), eq("jti-1"));
}

@Test
void blacklistAccessToken_setsKeyWithRemainingTtl() {
    tokenRevocationService.blacklistAccessToken("jti-access-1", 15000L);
    verify(valueOperations).set(eq("9paws:token:blacklist:jti-access-1"), eq("REVOKED"), eq(Duration.ofMillis(15000L)));
}

@Test
void isAccessTokenBlacklisted_whenKeyExists_returnsTrue() {
    when(redisTemplate.hasKey("9paws:token:blacklist:jti-access-1")).thenReturn(true);
    assertThat(tokenRevocationService.isAccessTokenBlacklisted("jti-access-1")).isTrue();
}

@Test
void revokeAllUserSessions_deletesAllRefreshKeysAndSessionSet() {
    UUID userId = UUID.randomUUID();
    when(setOperations.members("9paws:user_sessions:" + userId)).thenReturn(Set.of("jti-1", "jti-2"));
    tokenRevocationService.revokeAllUserSessions(userId);
    verify(redisTemplate).delete("9paws:refresh_token:" + userId + ":jti-1");
    verify(redisTemplate).delete("9paws:refresh_token:" + userId + ":jti-2");
    verify(redisTemplate).delete("9paws:user_sessions:" + userId);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TokenRevocationServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement `TokenRevocationService` and `TokenRevocationServiceImpl`**

Implement methods using Redis `opsForValue()` and `opsForSet()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=TokenRevocationServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/services/TokenRevocationService.java src/main/java/com/ninepaws/backend/services/impl/TokenRevocationServiceImpl.java src/test/java/com/ninepaws/backend/services/TokenRevocationServiceTest.java
git commit -m "feat(security): implement TokenRevocationService with Redis store and blacklist"
```

---

### Task 8: UserPrincipal, CustomUserDetailsService & SecurityUtils

**Files:**
- Create: `src/main/java/com/ninepaws/backend/security/UserPrincipal.java`
- Create: `src/main/java/com/ninepaws/backend/security/CustomUserDetailsService.java`
- Create: `src/main/java/com/ninepaws/backend/security/SecurityUtils.java`
- Test: `src/test/java/com/ninepaws/backend/security/CustomUserDetailsServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `User` entity
- Produces:
  - `UserPrincipal` implementing `UserDetails`: holds `UUID id`, `String email`, `String passwordHash`, `boolean emailVerified`, `Collection<? extends GrantedAuthority> authorities`.
  - `CustomUserDetailsService.loadUserByUsername(String email) -> UserDetails`
  - `CustomUserDetailsService.loadUserById(UUID userId) -> UserPrincipal`
  - `SecurityUtils.getCurrentUserId() -> UUID`
  - `SecurityUtils.getCurrentUserPrincipal() -> UserPrincipal`

- [ ] **Step 1: Write failing unit tests in `CustomUserDetailsServiceTest.java`**

```java
@Test
void loadUserByUsername_whenUserExists_returnsUserPrincipal() {
    User user = User.builder().fullName("Test").email("user@example.com").passwordHash("hash").emailVerified(true).build();
    user.setId(UUID.randomUUID());
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

    UserDetails details = userDetailsService.loadUserByUsername("user@example.com");
    assertThat(details.getUsername()).isEqualTo("user@example.com");
    assertThat(((UserPrincipal) details).getId()).isEqualTo(user.getId());
}

@Test
void loadUserByUsername_whenNotFound_throwsUsernameNotFoundException() {
    when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("notfound@example.com"))
        .isInstanceOf(UsernameNotFoundException.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=CustomUserDetailsServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement `UserPrincipal`, `CustomUserDetailsService`, and `SecurityUtils`**

Implement `UserPrincipal` mapping `User` fields to Spring Security `UserDetails`.
Implement `CustomUserDetailsService` fetching from `UserRepository`.
Implement `SecurityUtils` reading from `SecurityContextHolder`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=CustomUserDetailsServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/security/UserPrincipal.java src/main/java/com/ninepaws/backend/security/CustomUserDetailsService.java src/main/java/com/ninepaws/backend/security/SecurityUtils.java src/test/java/com/ninepaws/backend/security/CustomUserDetailsServiceTest.java
git commit -m "feat(security): implement UserPrincipal and CustomUserDetailsService"
```

---

### Task 9: JwtAuthenticationFilter & SecurityConfig

**Files:**
- Create: `src/main/java/com/ninepaws/backend/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/ninepaws/backend/security/SecurityConfig.java`
- Test: `src/test/java/com/ninepaws/backend/security/JwtAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `JwtService`, `TokenRevocationService`, `CustomUserDetailsService`
- Produces:
  - `JwtAuthenticationFilter` extending `OncePerRequestFilter`:
    - Checks header `Authorization: Bearer <token>`.
    - Validates token claims and ensures `tokenType.equals("ACCESS")`.
    - Checks `tokenRevocationService.isAccessTokenBlacklisted(jti)`. If true, rejects request / does not authenticate.
    - Sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`.
  - `SecurityConfig`:
    - `@Bean SecurityFilterChain`: stateless, permits public auth endpoints (`/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/verify-registration-otp`, `/api/v1/auth/resend-otp`, `/api/v1/auth/refresh-token`, `/api/v1/auth/forgot-password`, `/api/v1/auth/verify-forgot-password-otp`, `/api/v1/auth/reset-password`, Swagger `/v3/api-docs/**`, `/swagger-ui/**`, Actuator `/actuator/health`, public share `/api/v1/public/split-bills/*`), requires authentication for `/api/v1/auth/logout`, `/api/v1/auth/logout-all`, `/api/v1/auth/change-password` and all other endpoints.
    - Adds `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.
    - `@Bean PasswordEncoder`: returns `new BCryptPasswordEncoder(12)`.
    - `@Bean AuthenticationManager`.

- [ ] **Step 1: Write failing tests in `JwtAuthenticationFilterTest.java`**

```java
@Test
void whenValidAccessTokenAndNotBlacklisted_setsSecurityContextAuthentication() throws Exception {
    String token = "valid-token";
    TokenPayload payload = new TokenPayload(UUID.randomUUID(), "user@example.com", "jti-1", "ACCESS", Instant.now(), Instant.now().plusSeconds(1800));
    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-1")).thenReturn(false);
    when(userDetailsService.loadUserById(payload.userId())).thenReturn(mockPrincipal);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(mockPrincipal);
}

@Test
void whenAccessTokenIsBlacklisted_doesNotSetAuthentication() throws Exception {
    String token = "revoked-token";
    TokenPayload payload = new TokenPayload(UUID.randomUUID(), "user@example.com", "jti-blacklisted", "ACCESS", Instant.now(), Instant.now().plusSeconds(1800));
    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-blacklisted")).thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=JwtAuthenticationFilterTest`
Expected: FAIL

- [ ] **Step 3: Implement `JwtAuthenticationFilter` and `SecurityConfig`**

Implement filter with bearer token extraction, blacklisting check, and context populating. Configure `SecurityFilterChain` in `SecurityConfig`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=JwtAuthenticationFilterTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/security/JwtAuthenticationFilter.java src/main/java/com/ninepaws/backend/security/SecurityConfig.java src/test/java/com/ninepaws/backend/security/JwtAuthenticationFilterTest.java
git commit -m "feat(security): implement JwtAuthenticationFilter and SecurityConfig"
```

---

### Task 10: Auth Service Implementation

**Files:**
- Create: `src/main/java/com/ninepaws/backend/services/AuthService.java`
- Create: `src/main/java/com/ninepaws/backend/services/impl/AuthServiceImpl.java`
- Test: `src/test/java/com/ninepaws/backend/services/AuthServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `PasswordEncoder`, `JwtService`, `OtpService`, `EmailService`, `TokenRevocationService`
- Produces:
  - `void register(RegisterRequest request)`
  - `AuthResponse verifyRegistrationOtp(VerifyOtpRequest request)`
  - `void resendOtp(ResendOtpRequest request)`
  - `AuthResponse login(LoginRequest request)`
  - `AuthResponse refreshToken(RefreshTokenRequest request)`
  - `void logout(String accessToken, String refreshToken)`
  - `void logoutAll(UUID userId, String accessToken)`
  - `void forgotPassword(ForgotPasswordRequest request)`
  - `VerifyResetPasswordOtpResponse verifyForgotPasswordOtp(VerifyOtpRequest request)`
  - `void resetPassword(ResetPasswordRequest request)`
  - `void changePassword(UUID userId, ChangePasswordRequest request)`

- [ ] **Step 1: Write failing unit tests in `AuthServiceTest.java`**

```java
@Test
void register_whenEmailAlreadyExists_throwsDuplicateResourceException() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(true);
    RegisterRequest request = new RegisterRequest("User", "user@example.com", "Password123@");
    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(DuplicateResourceException.class);
}

@Test
void login_whenUserNotVerified_throwsAccountNotVerifiedException() {
    User user = User.builder().email("user@example.com").passwordHash("hash").emailVerified(false).build();
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Password123@", "hash")).thenReturn(true);

    LoginRequest request = new LoginRequest("user@example.com", "Password123@");
    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(AccountNotVerifiedException.class);
}

@Test
void refreshToken_whenTokenValid_rotatesAndIssuesNewTokens() {
    UUID userId = UUID.randomUUID();
    TokenPayload oldPayload = new TokenPayload(userId, "user@example.com", "jti-old", "REFRESH", Instant.now(), Instant.now().plusSeconds(604800));
    when(jwtService.parseAndValidateToken("old-refresh-token")).thenReturn(oldPayload);
    when(tokenRevocationService.isRefreshTokenValid(userId, "jti-old")).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().email("user@example.com").fullName("Test").build()));

    when(jwtService.generateAccessToken(eq(userId), eq("user@example.com"), anyString())).thenReturn("new-access");
    when(jwtService.generateRefreshToken(eq(userId), anyString())).thenReturn("new-refresh");

    AuthResponse res = authService.refreshToken(new RefreshTokenRequest("old-refresh-token"));

    assertThat(res.accessToken()).isEqualTo("new-access");
    assertThat(res.refreshToken()).isEqualTo("new-refresh");
    verify(tokenRevocationService).revokeRefreshToken(userId, "jti-old");
    verify(tokenRevocationService).recordRefreshToken(eq(userId), anyString(), eq("new-refresh"), anyLong());
}

@Test
void resetPassword_updatesPasswordAndRevokesAllUserSessions() {
    User user = User.builder().email("user@example.com").fullName("Test").build();
    user.setId(UUID.randomUUID());
    when(otpService.verifyPasswordResetToken("user@example.com", "valid-reset-token")).thenReturn(true);
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("NewPassword123@")).thenReturn("new-hash");

    authService.resetPassword(new ResetPasswordRequest("user@example.com", "valid-reset-token", "NewPassword123@"));

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    verify(tokenRevocationService).revokeAllUserSessions(user.getId());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement `AuthService` and `AuthServiceImpl`**

Implement all registration, verification, login, refresh rotation, logout, forgot password, and change password methods according to spec.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/services/AuthService.java src/main/java/com/ninepaws/backend/services/impl/AuthServiceImpl.java src/test/java/com/ninepaws/backend/services/AuthServiceTest.java
git commit -m "feat(auth): implement AuthService business logic"
```

---

### Task 11: Auth Controller & REST Endpoints

**Files:**
- Create: `src/main/java/com/ninepaws/backend/controllers/AuthController.java`
- Test: `src/test/java/com/ninepaws/backend/controllers/AuthControllerTest.java`

**Interfaces:**
- Consumes: `AuthService`, `SecurityUtils`
- Produces:
  - `POST /api/v1/auth/register` -> 201 Created `ApiResponse<Void>`
  - `POST /api/v1/auth/verify-registration-otp` -> 200 OK `ApiResponse<AuthResponse>`
  - `POST /api/v1/auth/resend-otp` -> 200 OK `ApiResponse<Void>`
  - `POST /api/v1/auth/login` -> 200 OK `ApiResponse<AuthResponse>`
  - `POST /api/v1/auth/refresh-token` -> 200 OK `ApiResponse<AuthResponse>`
  - `POST /api/v1/auth/logout` -> 200 OK `ApiResponse<Void>` (Authenticated)
  - `POST /api/v1/auth/logout-all` -> 200 OK `ApiResponse<Void>` (Authenticated)
  - `POST /api/v1/auth/forgot-password` -> 200 OK `ApiResponse<Void>`
  - `POST /api/v1/auth/verify-forgot-password-otp` -> 200 OK `ApiResponse<VerifyResetPasswordOtpResponse>`
  - `POST /api/v1/auth/reset-password` -> 200 OK `ApiResponse<Void>`
  - `POST /api/v1/auth/change-password` -> 200 OK `ApiResponse<Void>` (Authenticated)

- [ ] **Step 1: Write MockMvc tests in `AuthControllerTest.java`**

```java
@Test
void register_returns201Created() throws Exception {
    RegisterRequest req = new RegisterRequest("Test User", "test@ninepaws.com", "Password123@");

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));

    verify(authService).register(any(RegisterRequest.class));
}

@Test
void login_returns200AndAuthResponse() throws Exception {
    LoginRequest req = new LoginRequest("test@ninepaws.com", "Password123@");
    AuthResponse mockAuth = new AuthResponse("access", "refresh", "Bearer", 1800L, null);
    when(authService.login(any(LoginRequest.class))).thenReturn(mockAuth);

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AuthControllerTest`
Expected: FAIL

- [ ] **Step 3: Implement `AuthController`**

Annotate with `@RestController`, `@RequestMapping("/api/v1/auth")`, `@RequiredArgsConstructor`.
Use `@Valid` on request bodies. Delegate calls to `AuthService`. Extract Bearer token via `@RequestHeader(HttpHeaders.AUTHORIZATION)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AuthControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ninepaws/backend/controllers/AuthController.java src/test/java/com/ninepaws/backend/controllers/AuthControllerTest.java
git commit -m "feat(auth): implement AuthController REST endpoints"
```

---

### Task 12: End-to-End Integration & Security Flow Tests

**Files:**
- Create: `src/test/java/com/ninepaws/backend/integration/AuthIntegrationTest.java`

**Interfaces:**
- Consumes: Complete running Spring Boot context (`@SpringBootTest`, `@AutoConfigureMockMvc`), Mock `JavaMailSender`, Mock / Embedded `RedisTemplate`.
- Produces: Verified full security lifecycle test suite covering:
  - 1. User registration -> email OTP triggered.
  - 2. Email verification -> tokens received.
  - 3. Login with credentials -> returns valid access & refresh token.
  - 4. Access protected endpoint (`/api/v1/auth/change-password`) with access token -> 200 OK.
  - 5. Access protected endpoint with forged or expired token -> 401 Unauthorized.
  - 6. Token refresh -> rotates refresh token, old refresh token is revoked.
  - 7. Logout -> access token is blacklisted, immediate retry with same access token gives 401 Unauthorized.
  - 8. Forgot password -> verify OTP -> reset password -> old login fails, new login succeeds.

- [ ] **Step 1: Write integration tests in `AuthIntegrationTest.java`**

Implement end-to-end integration scenarios verifying the complete HTTP exchange, Redis interaction, and Spring Security filter chains.

- [ ] **Step 2: Run integration tests to verify they pass**

Run: `./mvnw test -Dtest=AuthIntegrationTest`
Expected: PASS

- [ ] **Step 3: Run full project test suite**

Run: `./mvnw test`
Expected: All tests PASS with zero regressions.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/ninepaws/backend/integration/AuthIntegrationTest.java
git commit -m "test(auth): add end-to-end authentication and security integration tests"
```
