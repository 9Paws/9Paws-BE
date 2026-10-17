# Authentication, JWT & Redis OTP System Specification (NinePaws Backend)

## 1. Intent & Context
NinePaws is a personal finance management and bill-splitting application. This specification defines the complete authentication and authorization system using email and password, stateless JWTs (access token + refresh token), Redis-backed token revocation, and 6-digit OTP verification delivered via Gmail SMTP.

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- Secure email & password registration with email verification via 6-digit OTP.
- Authentication returning short-lived JWT Access Token (30m) and long-lived Refresh Token (7d).
- Token rotation on refresh: consuming a refresh token issues a new access token and a new refresh token while revoking the old one.
- Token revocation (Logout & Blacklist): blacklisting unexpired access tokens via Redis JTI blacklist and deleting refresh tokens.
- Revoke all active sessions on demand or when password is reset.
- Forgot password flow: email -> 6-digit OTP verification -> one-time password-reset token -> new password.
- Change password flow for authenticated users with current password verification.
- Asynchronous Gmail OTP delivery with rate-limiting / cooldown to prevent abuse.
- Standardized API response format and global exception handling according to project guidelines.

### Non-Goals
- Google OAuth2 login (explicitly deferred for a subsequent phase per user request).
- Biometric authentication server-side challenge-response (stored flag only in user entity).
- SMS OTP delivery (Gmail SMTP only).

### Anti-Goals
- Never store plain text passwords (use `BCryptPasswordEncoder(12)`).
- Never store OTPs in PostgreSQL (use Redis with strict 120s TTL).
- Never return sensitive user data (password hash) in DTOs.
- Never use deprecated JJWT 0.11.x methods (use JJWT 0.12.6 modern fluent API).
- Never expose raw stack traces or internal exception details to API clients.

## 3. Architecture & Constraints

- **Platform & Runtime**: Java 25, Spring Boot 4.1.1, Spring Security.
- **Database**: PostgreSQL 16+ via Spring Data JPA (existing `users` table from `V1__init_ninepaws_schema.sql`).
- **Cache / In-Memory Store**: Redis via `StringRedisTemplate` for OTPs, refresh tokens, and blacklisted access tokens.
- **Mail Service**: `JavaMailSender` configured with Gmail SMTP (STARTTLS, port 587).
- **JWT**: `io.jsonwebtoken` (JJWT 0.12.6) with HMAC-SHA256 (`Keys.hmacShaKeyFor`). Base64 secret >= 256 bits (32 bytes).
- **DTOs**: Java 17+ `record` types with Jakarta Validation annotations (`@NotBlank`, `@Email`, `@Size`).
- **Package Layout**:
  - `com.ninepaws.backend.security`: `JwtService`, `JwtAuthenticationFilter`, `UserPrincipal`, `CustomUserDetailsService`, `SecurityConfig`, `SecurityUtils`.
  - `com.ninepaws.backend.services`: `AuthService`, `OtpService`, `EmailService`, `TokenRevocationService`.
  - `com.ninepaws.backend.controllers`: `AuthController`.
  - `com.ninepaws.backend.dtos.auth`: Request & Response records.
  - `com.ninepaws.backend.dtos.common`: `ApiResponse<T>`.
  - `com.ninepaws.backend.exceptions`: `GlobalExceptionHandler`, `AppException`, `InvalidOtpException`, `TokenRevokedException`, etc.
  - `com.ninepaws.backend.constants`: `SecurityConstants`, `RedisKeyConstants`.

## 4. Redis Key Naming & TTL Conventions

| Purpose | Redis Key Pattern | Value | TTL |
|---|---|---|---|
| Registration OTP | `9paws:otp:REGISTER:{email}` | 6-digit string | 120 seconds |
| Forgot Password OTP | `9paws:otp:FORGOT_PASSWORD:{email}` | 6-digit string | 120 seconds |
| OTP Resend Cooldown | `9paws:otp:cooldown:{type}:{email}` | `"SENT"` | 60 seconds |
| Active Refresh Token | `9paws:refresh_token:{userId}:{jti}` | Refresh Token string | 7 days (matching JWT refresh exp) |
| User Active Sessions Set | `9paws:user_sessions:{userId}` | Set of active `jti`s | 7 days |
| Access Token Blacklist | `9paws:token:blacklist:{jti}` | `"REVOKED"` | Remaining access token lifespan |
| Password Reset Token | `9paws:password_reset_token:{email}` | UUID / Secret String | 10 minutes |

## 5. Detailed Flows

### 5.1 Registration & OTP Verification
1. `POST /api/v1/auth/register`
   - Request: `{ "fullName": "Nguyen Van A", "email": "a@example.com", "password": "Password123@" }`
   - Checks if email exists. If exists, returns 409 Conflict.
   - Saves User with `emailVerified = false`, `authProvider = LOCAL`, `passwordHash = bcrypt(password)`.
   - Generates 6-digit OTP using `SecureRandom`, stores in Redis `9paws:otp:REGISTER:{email}` (120s TTL), sets cooldown (60s).
   - Triggers async email sending via Gmail SMTP.
   - Response: 201 Created with message "Đăng ký thành công. Vui lòng kiểm tra email để lấy mã OTP xác thực tài khoản."
2. `POST /api/v1/auth/verify-registration-otp`
   - Request: `{ "email": "a@example.com", "otp": "123456" }`
   - Checks Redis OTP key. If invalid or expired, throws `InvalidOtpException` (400 Bad Request).
   - Deletes OTP key immediately (prevent replay).
   - Updates User: `emailVerified = true`.
   - Issues Access Token & Refresh Token, records Refresh Token in Redis, returns 200 OK with `AuthResponse`.

### 5.2 Resend OTP
- `POST /api/v1/auth/resend-otp`
  - Request: `{ "email": "a@example.com", "type": "REGISTER" | "FORGOT_PASSWORD" }`
  - Verifies user exists.
  - Checks cooldown key `9paws:otp:cooldown:{type}:{email}`. If present, throws `RateLimitException` (429 Too Many Requests).
  - Generates new 6-digit OTP, updates Redis key (120s TTL), sets cooldown (60s TTL), dispatches email.

### 5.3 Login
- `POST /api/v1/auth/login`
  - Request: `{ "email": "a@example.com", "password": "Password123@" }`
  - Finds user by email. If not found or `passwordEncoder.matches` fails -> 401 Unauthorized (`BadCredentialsException`).
  - Checks `emailVerified`. If `false` -> throws 403 Forbidden with code `ACCOUNT_NOT_VERIFIED`.
  - Generates unique `jti` (UUID) for access token and refresh token.
  - Generates Access Token (30m) & Refresh Token (7d).
  - Saves refresh token `jti` in Redis `9paws:refresh_token:{userId}:{jti}` and adds to `9paws:user_sessions:{userId}`.
  - Returns 200 OK with `AuthResponse`.

### 5.4 Refresh Token & Token Rotation
- `POST /api/v1/auth/refresh-token`
  - Request: `{ "refreshToken": "..." }`
  - Parses and verifies refresh token signature and expiration.
  - Extracts `userId` and `jti`.
  - Checks if `9paws:refresh_token:{userId}:{jti}` exists in Redis. If absent -> throws 401 Unauthorized (`TokenRevokedException`).
  - Token Rotation:
    - Deletes old `jti` from Redis.
    - Generates new `jti_new` for new refresh token, and new access token.
    - Saves new refresh token to Redis.
    - Returns 200 OK with new `AuthResponse`.

### 5.5 Revoke Token & Logout
1. `POST /api/v1/auth/logout` (Authenticated via Bearer Token)
   - Request: `{ "refreshToken": "..." }`
   - From Bearer access token: extracts `jti` and remaining TTL. Stores in Redis `9paws:token:blacklist:{jti}` with remaining TTL.
   - From body `refreshToken`: extracts `userId` and `jti`. Deletes `9paws:refresh_token:{userId}:{jti}` and removes from `9paws:user_sessions:{userId}`.
   - Returns 200 OK with message "Đăng xuất thành công."
2. `POST /api/v1/auth/logout-all` (Authenticated via Bearer Token)
   - Blacklists current access token.
   - Fetches all `jti`s in `9paws:user_sessions:{userId}`, deletes all associated `9paws:refresh_token:{userId}:{jti}` keys, and deletes the session set.
   - Returns 200 OK with message "Đã đăng xuất khỏi tất cả các thiết bị."

### 5.6 Forgot Password & Reset Password
1. `POST /api/v1/auth/forgot-password`
   - Request: `{ "email": "a@example.com" }`
   - Checks if user exists. If not, returns generic success response to prevent email enumeration.
   - If user exists: checks cooldown (60s), generates 6-digit OTP, saves in `9paws:otp:FORGOT_PASSWORD:{email}` (120s TTL), sends email.
   - Returns 200 OK with message "Mã OTP đặt lại mật khẩu đã được gửi đến email của bạn."
2. `POST /api/v1/auth/verify-forgot-password-otp`
   - Request: `{ "email": "a@example.com", "otp": "123456" }`
   - Verifies OTP against Redis.
   - If valid: deletes OTP key, generates secure `resetToken` (UUID), stores in Redis `9paws:password_reset_token:{email}` (10m TTL).
   - Returns 200 OK with `{ "resetToken": "...", "email": "a@example.com" }`.
3. `POST /api/v1/auth/reset-password`
   - Request: `{ "email": "a@example.com", "resetToken": "...", "newPassword": "NewPassword123@" }`
   - Verifies `resetToken` against Redis key `9paws:password_reset_token:{email}`.
   - If match: updates `passwordHash = bcrypt(newPassword)`, deletes `resetToken`, revokes all active refresh tokens for the user in Redis.
   - Returns 200 OK with message "Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới."

### 5.7 Change Password
- `POST /api/v1/auth/change-password` (Authenticated via Bearer Token)
  - Request: `{ "currentPassword": "...", "newPassword": "...", "confirmPassword": "..." }`
  - Extracts current user from `SecurityContext`.
  - Verifies `confirmPassword.equals(newPassword)` and `!newPassword.equals(currentPassword)`.
  - Verifies `passwordEncoder.matches(currentPassword, user.getPasswordHash())`. If mismatch -> 400 Bad Request.
  - Updates password in database.
  - Revokes all other active refresh sessions for the user to force re-login on other devices.
  - Returns 200 OK with message "Đổi mật khẩu thành công."

## 6. Security Filter Chain & Blacklist Enforcement
- `JwtAuthenticationFilter`:
  - Intercepts requests with `Authorization: Bearer <token>`.
  - Extracts token, validates JWT signature and expiry via `JwtService`.
  - Extracts `jti`. Calls `TokenRevocationService.isBlacklisted(jti)`. If blacklisted, does not authenticate (or writes 401 Unauthorized).
  - Loads user claims, constructs `UserPrincipal`, and populates `SecurityContextHolder.getContext().setAuthentication(...)`.
- `SecurityConfig`:
  - Permit all on:
    - `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/verify-registration-otp`, `/api/v1/auth/resend-otp`
    - `/api/v1/auth/refresh-token`
    - `/api/v1/auth/forgot-password`, `/api/v1/auth/verify-forgot-password-otp`, `/api/v1/auth/reset-password`
    - `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health`
    - `GET /api/v1/public/split-bills/*`
  - Authenticated required for:
    - `/api/v1/auth/logout`, `/api/v1/auth/logout-all`, `/api/v1/auth/change-password`
    - All other `/api/v1/**` endpoints.
