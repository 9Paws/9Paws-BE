package com.ninepaws.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninepaws.backend.constants.RedisKeyConstants;
import com.ninepaws.backend.dtos.auth.ChangePasswordRequest;
import com.ninepaws.backend.dtos.auth.ForgotPasswordRequest;
import com.ninepaws.backend.dtos.auth.LoginRequest;
import com.ninepaws.backend.dtos.auth.RefreshTokenRequest;
import com.ninepaws.backend.dtos.auth.RegisterRequest;
import com.ninepaws.backend.dtos.auth.ResetPasswordRequest;
import com.ninepaws.backend.dtos.auth.VerifyOtpRequest;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.enums.OtpType;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.JwtService;
import com.ninepaws.backend.security.TokenPayload;
import com.ninepaws.backend.services.TokenRevocationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Authentication & Security Flow Integration Tests")
class AuthIntegrationTest {

  private static final String DEFAULT_PASSWORD = "Password123!";
  private static final String UPDATED_PASSWORD = "NewPassword456!";
  private static final String RESET_PASSWORD = "ResetPassword789!";

  private static final String EMAIL_SCENARIO_1 = "it.scenario1.register@ninepaws.com";
  private static final String EMAIL_SCENARIO_2 = "it.scenario2.verify@ninepaws.com";
  private static final String EMAIL_SCENARIO_3 = "it.scenario3.login@ninepaws.com";
  private static final String EMAIL_SCENARIO_4 = "it.scenario4.protect@ninepaws.com";
  private static final String EMAIL_SCENARIO_5 = "it.scenario5.security@ninepaws.com";
  private static final String EMAIL_SCENARIO_6 = "it.scenario6.refresh@ninepaws.com";
  private static final String EMAIL_SCENARIO_7 = "it.scenario7.logout@ninepaws.com";
  private static final String EMAIL_SCENARIO_8 = "it.scenario8.forgot@ninepaws.com";
  private static final String EMAIL_E2E = "it.scenario.e2e.lifecycle@ninepaws.com";

  private static final List<String> ALL_TEST_EMAILS =
      List.of(
          EMAIL_SCENARIO_1,
          EMAIL_SCENARIO_2,
          EMAIL_SCENARIO_3,
          EMAIL_SCENARIO_4,
          EMAIL_SCENARIO_5,
          EMAIL_SCENARIO_6,
          EMAIL_SCENARIO_7,
          EMAIL_SCENARIO_8,
          EMAIL_E2E);

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Autowired private UserRepository userRepository;

  @Autowired private StringRedisTemplate redisTemplate;

  @Autowired private JwtService jwtService;

  @Autowired private TokenRevocationService tokenRevocationService;

  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private JavaMailSender mailSender;

  @Value("${app.jwt.secret}")
  private String jwtSecretBase64;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    cleanAllTestUsersAndRedis();
  }

  @AfterEach
  void tearDown() {
    cleanAllTestUsersAndRedis();
  }

  private void cleanAllTestUsersAndRedis() {
    for (String email : ALL_TEST_EMAILS) {
      userRepository
          .findByEmail(email)
          .ifPresent(
              user -> {
                tokenRevocationService.revokeAllUserSessions(user.getId());
                userRepository.delete(user);
              });

      redisTemplate.delete(RedisKeyConstants.otpKey(OtpType.REGISTER, email));
      redisTemplate.delete(RedisKeyConstants.otpCooldownKey(OtpType.REGISTER, email));
      redisTemplate.delete(RedisKeyConstants.otpKey(OtpType.FORGOT_PASSWORD, email));
      redisTemplate.delete(RedisKeyConstants.otpCooldownKey(OtpType.FORGOT_PASSWORD, email));
      redisTemplate.delete(RedisKeyConstants.passwordResetTokenKey(email));
    }
  }

  private User createVerifiedUser(String email, String rawPassword) {
    User user =
        User.builder()
            .fullName("Integration Test User")
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .authProvider(AuthProvider.LOCAL)
            .emailVerified(true)
            .biometricEnabled(false)
            .dailyReminderEnabled(true)
            .currency("VND")
            .language("vi")
            .build();
    return userRepository.saveAndFlush(user);
  }

  // =========================================================================
  // SCENARIO 1: User registration -> email OTP triggered
  // =========================================================================
  @Test
  @Order(1)
  @DisplayName("Scenario 1: User registration -> persists unverified user and triggers email OTP")
  void scenario1_userRegistration_triggersEmailOtp() throws Exception {
    RegisterRequest registerRequest =
        new RegisterRequest("Nguyễn Văn An", EMAIL_SCENARIO_1, DEFAULT_PASSWORD);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "User registered successfully. Please verify your email with the OTP sent."));

    // Verify unverified user in database
    var userOpt = userRepository.findByEmail(EMAIL_SCENARIO_1);
    assertThat(userOpt).isPresent();
    User savedUser = userOpt.get();
    assertThat(savedUser.getFullName()).isEqualTo("Nguyễn Văn An");
    assertThat(savedUser.isEmailVerified()).isFalse();

    // Verify OTP generated and stored in Redis
    String otpKey = RedisKeyConstants.otpKey(OtpType.REGISTER, EMAIL_SCENARIO_1);
    String otp = redisTemplate.opsForValue().get(otpKey);
    assertThat(otp).isNotNull().matches("^\\d{6}$");

    // Verify email service triggered mailSender
    verify(mailSender, timeout(3000).atLeastOnce()).send(any(MimeMessage.class));
  }

  // =========================================================================
  // SCENARIO 2: Email verification -> tokens received
  // =========================================================================
  @Test
  @Order(2)
  @DisplayName(
      "Scenario 2: Email verification -> verifies OTP, marks email verified, returns tokens")
  void scenario2_emailVerification_returnsTokens() throws Exception {
    RegisterRequest registerRequest =
        new RegisterRequest("Trần Thị Bình", EMAIL_SCENARIO_2, DEFAULT_PASSWORD);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    String otpKey = RedisKeyConstants.otpKey(OtpType.REGISTER, EMAIL_SCENARIO_2);
    String otp = redisTemplate.opsForValue().get(otpKey);
    assertThat(otp).isNotNull();

    VerifyOtpRequest verifyOtpRequest = new VerifyOtpRequest(EMAIL_SCENARIO_2, otp);

    MvcResult verifyResult =
        mockMvc
            .perform(
                post("/api/v1/auth/verify-registration-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(verifyOtpRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.httpStatus").value(200))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.data.user.email").value(EMAIL_SCENARIO_2))
            .andExpect(jsonPath("$.data.user.emailVerified").value(true))
            .andReturn();

    // Verify database state updated
    User user = userRepository.findByEmail(EMAIL_SCENARIO_2).orElseThrow();
    assertThat(user.isEmailVerified()).isTrue();

    // Verify OTP key was consumed and deleted from Redis
    assertThat(redisTemplate.hasKey(otpKey)).isFalse();

    // Verify returned access token is valid
    JsonNode jsonNode = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
    String accessToken = jsonNode.path("data").path("accessToken").asText();
    TokenPayload payload = jwtService.parseAndValidateToken(accessToken);
    assertThat(payload).isNotNull();
    assertThat(payload.userId()).isEqualTo(user.getId());
    assertThat(payload.tokenType()).isEqualTo("ACCESS");
  }

  // =========================================================================
  // SCENARIO 3: Login with credentials -> returns valid access & refresh token
  // =========================================================================
  @Test
  @Order(3)
  @DisplayName("Scenario 3: Login with credentials -> returns valid access & refresh token")
  void scenario3_loginWithCredentials_returnsValidTokens() throws Exception {
    User user = createVerifiedUser(EMAIL_SCENARIO_3, DEFAULT_PASSWORD);

    LoginRequest loginRequest = new LoginRequest(EMAIL_SCENARIO_3, DEFAULT_PASSWORD);

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.httpStatus").value(200))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.data.user.email").value(EMAIL_SCENARIO_3))
            .andReturn();

    JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    String accessToken = json.path("data").path("accessToken").asText();
    String refreshToken = json.path("data").path("refreshToken").asText();

    TokenPayload accessPayload = jwtService.parseAndValidateToken(accessToken);
    assertThat(accessPayload).isNotNull();
    assertThat(accessPayload.userId()).isEqualTo(user.getId());
    assertThat(accessPayload.tokenType()).isEqualTo("ACCESS");

    TokenPayload refreshPayload = jwtService.parseAndValidateToken(refreshToken);
    assertThat(refreshPayload).isNotNull();
    assertThat(refreshPayload.userId()).isEqualTo(user.getId());
    assertThat(refreshPayload.tokenType()).isEqualTo("REFRESH");
    assertThat(tokenRevocationService.isRefreshTokenValid(user.getId(), refreshPayload.jti()))
        .isTrue();

    // Login with incorrect password must fail with 401 Unauthorized
    LoginRequest badPasswordRequest = new LoginRequest(EMAIL_SCENARIO_3, "WrongPassword999!");
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badPasswordRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  // =========================================================================
  // SCENARIO 4: Access protected endpoint (/api/v1/auth/change-password) with access token -> 200
  // OK
  // =========================================================================
  @Test
  @Order(4)
  @DisplayName("Scenario 4: Access protected endpoint with valid access token -> 200 OK")
  void scenario4_accessProtectedEndpoint_withAccessToken_returns200() throws Exception {
    User user = createVerifiedUser(EMAIL_SCENARIO_4, DEFAULT_PASSWORD);

    // Login to obtain valid access token
    LoginRequest loginRequest = new LoginRequest(EMAIL_SCENARIO_4, DEFAULT_PASSWORD);
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    String accessToken =
        objectMapper
            .readTree(loginResult.getResponse().getContentAsString())
            .path("data")
            .path("accessToken")
            .asText();

    ChangePasswordRequest changePasswordRequest =
        new ChangePasswordRequest(DEFAULT_PASSWORD, UPDATED_PASSWORD, UPDATED_PASSWORD);

    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changePasswordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Password changed successfully"));

    // Verify password was actually updated in database
    User updatedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(passwordEncoder.matches(UPDATED_PASSWORD, updatedUser.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, updatedUser.getPasswordHash())).isFalse();
  }

  // =========================================================================
  // SCENARIO 5: Access protected endpoint with forged or expired token -> 401 Unauthorized
  // =========================================================================
  @Test
  @Order(5)
  @DisplayName(
      "Scenario 5: Access protected endpoint with forged or expired token -> 401 Unauthorized")
  void scenario5_accessProtectedEndpoint_withForgedOrExpiredToken_returns401() throws Exception {
    User user = createVerifiedUser(EMAIL_SCENARIO_5, DEFAULT_PASSWORD);

    ChangePasswordRequest changePasswordRequest =
        new ChangePasswordRequest(DEFAULT_PASSWORD, UPDATED_PASSWORD, UPDATED_PASSWORD);
    String requestBody = objectMapper.writeValueAsString(changePasswordRequest);

    // 5a. Request with no Authorization header -> 401 Unauthorized
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.errorCode").value("WEB_401"));

    // 5b. Request with forged token -> 401 Unauthorized
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer forged.invalid.token.signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.errorCode").value("WEB_401"));

    // 5c. Request with expired token -> 401 Unauthorized
    String expiredToken =
        Jwts.builder()
            .subject(user.getId().toString())
            .id(UUID.randomUUID().toString())
            .claim("email", user.getEmail())
            .claim("type", "ACCESS")
            .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .expiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretBase64)))
            .compact();

    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.errorCode").value("WEB_401"));

    // 5d. Request using REFRESH token as Bearer token -> 401 Unauthorized
    String refreshToken =
        jwtService.generateRefreshToken(user.getId(), UUID.randomUUID().toString());

    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.errorCode").value("WEB_401"));
  }

  // =========================================================================
  // SCENARIO 6: Token refresh -> rotates refresh token, old refresh token is revoked
  // =========================================================================
  @Test
  @Order(6)
  @DisplayName("Scenario 6: Token refresh -> rotates refresh token, old refresh token is revoked")
  void scenario6_tokenRefresh_rotatesAndRevokesOldRefreshToken() throws Exception {
    User user = createVerifiedUser(EMAIL_SCENARIO_6, DEFAULT_PASSWORD);

    // Initial login
    LoginRequest loginRequest = new LoginRequest(EMAIL_SCENARIO_6, DEFAULT_PASSWORD);
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    String oldRefreshToken = loginJson.path("data").path("refreshToken").asText();
    TokenPayload oldRefreshPayload = jwtService.parseAndValidateToken(oldRefreshToken);
    assertThat(oldRefreshPayload).isNotNull();
    String oldJti = oldRefreshPayload.jti();

    // Verify initial refresh token is valid in Redis
    assertThat(tokenRevocationService.isRefreshTokenValid(user.getId(), oldJti)).isTrue();

    // Perform refresh token rotation
    RefreshTokenRequest refreshRequest = new RefreshTokenRequest(oldRefreshToken);
    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.httpStatus").value(200))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn();

    JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
    String newRefreshToken = refreshJson.path("data").path("refreshToken").asText();
    String newAccessToken = refreshJson.path("data").path("accessToken").asText();

    // Verify new refresh token is distinct
    assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
    TokenPayload newRefreshPayload = jwtService.parseAndValidateToken(newRefreshToken);
    assertThat(newRefreshPayload).isNotNull();
    assertThat(tokenRevocationService.isRefreshTokenValid(user.getId(), newRefreshPayload.jti()))
        .isTrue();

    // Verify old refresh token is now revoked in Redis
    assertThat(tokenRevocationService.isRefreshTokenValid(user.getId(), oldJti)).isFalse();

    // Replay attack with revoked old refresh token must be rejected with 401 Unauthorized
    mockMvc
        .perform(
            post("/api/v1/auth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_104"))
        .andExpect(jsonPath("$.message").value("Refresh token is revoked or invalid"));

    // Verify new access token works for protected endpoints
    ChangePasswordRequest changePasswordRequest =
        new ChangePasswordRequest(DEFAULT_PASSWORD, UPDATED_PASSWORD, UPDATED_PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changePasswordRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200));
  }

  // =========================================================================
  // SCENARIO 7: Logout -> access token is blacklisted, immediate retry gives 401
  // =========================================================================
  @Test
  @Order(7)
  @DisplayName(
      "Scenario 7: Logout -> access token is blacklisted, immediate retry with same token returns 401")
  void scenario7_logout_blacklistsAccessToken_andImmediateRetryReturns401() throws Exception {
    User user = createVerifiedUser(EMAIL_SCENARIO_7, DEFAULT_PASSWORD);

    // Login to obtain valid tokens
    LoginRequest loginRequest = new LoginRequest(EMAIL_SCENARIO_7, DEFAULT_PASSWORD);
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    String accessToken = json.path("data").path("accessToken").asText();
    String refreshToken = json.path("data").path("refreshToken").asText();

    TokenPayload accessPayload = jwtService.parseAndValidateToken(accessToken);
    assertThat(accessPayload).isNotNull();
    String accessJti = accessPayload.jti();

    // Prior to logout, access token is not blacklisted
    assertThat(tokenRevocationService.isAccessTokenBlacklisted(accessJti)).isFalse();

    // Perform logout
    RefreshTokenRequest logoutRequest = new RefreshTokenRequest(refreshToken);
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Logged out successfully"));

    // Verify access token JTI is now blacklisted in Redis
    assertThat(tokenRevocationService.isAccessTokenBlacklisted(accessJti)).isTrue();
    assertThat(redisTemplate.hasKey(RedisKeyConstants.tokenBlacklistKey(accessJti))).isTrue();

    // Immediate retry on protected endpoint with the blacklisted access token gives 401
    ChangePasswordRequest changePasswordRequest =
        new ChangePasswordRequest(DEFAULT_PASSWORD, UPDATED_PASSWORD, UPDATED_PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changePasswordRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.errorCode").value("WEB_401"));

    // Repeated logout with the blacklisted access token also gives 401
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
        .andExpect(status().isUnauthorized());
  }

  // =========================================================================
  // SCENARIO 8: Forgot password -> verify OTP -> reset password -> old login fails, new login
  // succeeds
  // =========================================================================
  @Test
  @Order(8)
  @DisplayName(
      "Scenario 8: Forgot password -> verify OTP -> reset password -> old login fails, new login succeeds")
  void scenario8_forgotPassword_verifyOtp_resetPassword_loginLifecycle() throws Exception {
    createVerifiedUser(EMAIL_SCENARIO_8, DEFAULT_PASSWORD);

    // 8a. Forgot password request triggers OTP
    ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest(EMAIL_SCENARIO_8);
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(
            jsonPath("$.message").value("Password reset OTP sent to email if account exists"));

    verify(mailSender, timeout(3000).atLeastOnce()).send(any(MimeMessage.class));

    String otpKey = RedisKeyConstants.otpKey(OtpType.FORGOT_PASSWORD, EMAIL_SCENARIO_8);
    String otp = redisTemplate.opsForValue().get(otpKey);
    assertThat(otp).isNotNull().matches("^\\d{6}$");

    // 8b. Verify forgot password OTP -> returns password reset token
    VerifyOtpRequest verifyOtpRequest = new VerifyOtpRequest(EMAIL_SCENARIO_8, otp);
    MvcResult verifyOtpResult =
        mockMvc
            .perform(
                post("/api/v1/auth/verify-forgot-password-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(verifyOtpRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.httpStatus").value(200))
            .andExpect(jsonPath("$.data.email").value(EMAIL_SCENARIO_8))
            .andExpect(jsonPath("$.data.resetToken").isNotEmpty())
            .andReturn();

    // Verify OTP was consumed
    assertThat(redisTemplate.hasKey(otpKey)).isFalse();

    String resetToken =
        objectMapper
            .readTree(verifyOtpResult.getResponse().getContentAsString())
            .path("data")
            .path("resetToken")
            .asText();

    // 8c. Reset password using reset token
    ResetPasswordRequest resetRequest =
        new ResetPasswordRequest(EMAIL_SCENARIO_8, resetToken, RESET_PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(
            jsonPath("$.message")
                .value("Password reset successfully. Please login with your new password."));

    // 8d. Old password login must fail with 401 Unauthorized
    LoginRequest oldLoginRequest = new LoginRequest(EMAIL_SCENARIO_8, DEFAULT_PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(oldLoginRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.httpStatus").value(401))
        .andExpect(jsonPath("$.message").value("Invalid email or password"));

    // 8e. New password login must succeed with 200 OK
    LoginRequest newLoginRequest = new LoginRequest(EMAIL_SCENARIO_8, RESET_PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newLoginRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.data.user.email").value(EMAIL_SCENARIO_8));
  }

  // =========================================================================
  // FULL END-TO-END UNIFIED USER JOURNEY LIFECYCLE
  // =========================================================================
  @Test
  @Order(9)
  @DisplayName(
      "Full Unified Lifecycle: Register -> Verify -> Login -> Protected -> Rotate -> Logout -> Reset")
  void shouldExecuteCompleteEndToEndSecurityLifecycle() throws Exception {
    // 1. Register
    RegisterRequest registerRequest =
        new RegisterRequest("Full Flow User", EMAIL_E2E, DEFAULT_PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    // 2. Cannot login before verifying email
    LoginRequest earlyLoginRequest = new LoginRequest(EMAIL_E2E, DEFAULT_PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(earlyLoginRequest)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.httpStatus").value(403))
        .andExpect(jsonPath("$.errorCode").value("AUTH_103"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Account is not verified. Please verify your email via OTP before logging in."));

    // 3. Verify registration OTP
    String regOtp =
        redisTemplate.opsForValue().get(RedisKeyConstants.otpKey(OtpType.REGISTER, EMAIL_E2E));
    assertThat(regOtp).isNotNull();

    mockMvc
        .perform(
            post("/api/v1/auth/verify-registration-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new VerifyOtpRequest(EMAIL_E2E, regOtp))))
        .andExpect(status().isOk());

    // 4. Login after verification
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earlyLoginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    String accessToken1 = loginJson.path("data").path("accessToken").asText();
    String refreshToken1 = loginJson.path("data").path("refreshToken").asText();

    // 5. Access protected endpoint with access token
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangePasswordRequest(
                            DEFAULT_PASSWORD, UPDATED_PASSWORD, UPDATED_PASSWORD))))
        .andExpect(status().isOk());

    // 6. Login with updated password to obtain fresh token set
    MvcResult reLoginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest(EMAIL_E2E, UPDATED_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode reLoginJson = objectMapper.readTree(reLoginResult.getResponse().getContentAsString());
    String accessToken2 = reLoginJson.path("data").path("accessToken").asText();
    String refreshToken2 = reLoginJson.path("data").path("refreshToken").asText();

    // 7. Refresh token rotation
    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken2))))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
    String accessToken3 = refreshJson.path("data").path("accessToken").asText();
    String refreshToken3 = refreshJson.path("data").path("refreshToken").asText();

    // Old refresh token 2 is now revoked
    mockMvc
        .perform(
            post("/api/v1/auth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken2))))
        .andExpect(status().isUnauthorized());

    // 8. Logout with accessToken3
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken3)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken3))))
        .andExpect(status().isOk());

    // Immediate reuse of blacklisted accessToken3 gives 401
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken3)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangePasswordRequest(
                            UPDATED_PASSWORD, RESET_PASSWORD, RESET_PASSWORD))))
        .andExpect(status().isUnauthorized());

    // 9. Forgot password lifecycle
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(EMAIL_E2E))))
        .andExpect(status().isOk());

    String fpOtp =
        redisTemplate
            .opsForValue()
            .get(RedisKeyConstants.otpKey(OtpType.FORGOT_PASSWORD, EMAIL_E2E));
    assertThat(fpOtp).isNotNull();

    MvcResult verifyFpResult =
        mockMvc
            .perform(
                post("/api/v1/auth/verify-forgot-password-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new VerifyOtpRequest(EMAIL_E2E, fpOtp))))
            .andExpect(status().isOk())
            .andReturn();

    String resetToken =
        objectMapper
            .readTree(verifyFpResult.getResponse().getContentAsString())
            .path("data")
            .path("resetToken")
            .asText();

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResetPasswordRequest(EMAIL_E2E, resetToken, RESET_PASSWORD))))
        .andExpect(status().isOk());

    // Old login fails
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest(EMAIL_E2E, UPDATED_PASSWORD))))
        .andExpect(status().isUnauthorized());

    // Final new login succeeds
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest(EMAIL_E2E, RESET_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200));
  }
}
