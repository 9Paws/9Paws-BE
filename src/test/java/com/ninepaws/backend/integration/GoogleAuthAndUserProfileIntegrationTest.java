package com.ninepaws.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninepaws.backend.dtos.auth.GoogleLoginRequest;
import com.ninepaws.backend.dtos.user.UpdateProfileRequest;
import com.ninepaws.backend.dtos.user.UpdateSettingsRequest;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.JwtService;
import com.ninepaws.backend.security.oauth.GoogleTokenPayload;
import com.ninepaws.backend.security.oauth.GoogleTokenVerifier;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@DisplayName("Module 1: Google OAuth2 & User Profile Integration Tests")
class GoogleAuthAndUserProfileIntegrationTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtService jwtService;

  @MockitoBean private GoogleTokenVerifier googleTokenVerifier;

  private static final String GOOGLE_EMAIL = "mai.nguyen.it@gmail.com";
  private static final String GOOGLE_SUB = "google-sub-it-999999";
  private static final String LOCAL_EMAIL = "local.user.it@gmail.com";
  private static final String LOCAL_GOOGLE_SUB = "google-sub-local-link-888888";

  @AfterEach
  void cleanUp() {
    userRepository.findByEmail(GOOGLE_EMAIL).ifPresent(userRepository::delete);
    userRepository.findByEmail(LOCAL_EMAIL).ifPresent(userRepository::delete);
  }

  @Test
  @DisplayName("Full Lifecycle: Google Sign-Up -> View Profile -> Update Profile -> Patch Settings")
  void shouldPerformFullGoogleUserLifecycle() throws Exception {
    String rawIdToken = "google.token.lifecycle";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            GOOGLE_SUB, GOOGLE_EMAIL, "Mai Nguyễn", "https://avatar.png/mai.png", true);
    when(googleTokenVerifier.verify(rawIdToken)).thenReturn(payload);

    // 1. Google Sign-Up
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new GoogleLoginRequest(rawIdToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.httpStatus").value(200))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.user.email").value(GOOGLE_EMAIL))
            .andExpect(jsonPath("$.data.user.fullName").value("Mai Nguyễn"))
            .andReturn();

    JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    String accessToken = jsonNode.path("data").path("accessToken").asText();
    String bearer = "Bearer " + accessToken;

    // 2. View Profile
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.email").value(GOOGLE_EMAIL))
        .andExpect(jsonPath("$.data.authProvider").value("GOOGLE"))
        .andExpect(jsonPath("$.data.isGoogleLinked").value(true))
        .andExpect(jsonPath("$.data.companionDays").value(0))
        .andExpect(jsonPath("$.data.settings.dailyReminderTime").value("21:00"));

    // 3. Update Profile (Name & Phone)
    UpdateProfileRequest updateProfileReq =
        new UpdateProfileRequest("Mai Nguyễn Hoàng", "0987654321");
    mockMvc
        .perform(
            put("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateProfileReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.fullName").value("Mai Nguyễn Hoàng"))
        .andExpect(jsonPath("$.data.phoneNumber").value("0987654321"));

    // 4. Update Settings (Biometric & Reminder time)
    UpdateSettingsRequest updateSettingsReq =
        new UpdateSettingsRequest(true, true, "22:15", "VND", "vi");
    mockMvc
        .perform(
            patch("/api/v1/users/me/settings")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateSettingsReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.biometricEnabled").value(true))
        .andExpect(jsonPath("$.data.dailyReminderTime").value("22:15"));
  }

  @Test
  @DisplayName("Anti-Account Takeover -> Local Login -> Link Google -> Google Sign-In Success")
  void shouldPreventTakeoverAndAllowExplicitLinking() throws Exception {
    // 1. Create existing local user with password
    User localUser =
        User.builder()
            .fullName("Local Account User")
            .email(LOCAL_EMAIL)
            .passwordHash(passwordEncoder.encode("Secret123!"))
            .authProvider(AuthProvider.LOCAL)
            .oauthId(null)
            .emailVerified(true)
            .build();
    User savedLocalUser = userRepository.saveAndFlush(localUser);

    String linkToken = "google.token.link";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            LOCAL_GOOGLE_SUB, LOCAL_EMAIL, "Local Account User", null, true);
    when(googleTokenVerifier.verify(linkToken)).thenReturn(payload);

    // 2. Attempt Google login before linking -> MUST return 409 Conflict
    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GoogleLoginRequest(linkToken))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("AUTH_107"));

    // 3. User logs in with local credentials (or generates bearer token)
    String accessJti = UUID.randomUUID().toString();
    String localAccessToken =
        jwtService.generateAccessToken(savedLocalUser.getId(), LOCAL_EMAIL, accessJti);
    String localBearer = "Bearer " + localAccessToken;

    // 4. Explicit Account Linking via POST /api/v1/users/me/link-google
    mockMvc
        .perform(
            post("/api/v1/users/me/link-google")
                .header(HttpHeaders.AUTHORIZATION, localBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GoogleLoginRequest(linkToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.isGoogleLinked").value(true))
        .andExpect(jsonPath("$.data.email").value(LOCAL_EMAIL));

    // 5. Subsequent Google login now SUCCEEDS
    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GoogleLoginRequest(linkToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.user.email").value(LOCAL_EMAIL));

    // Verify authProvider remains LOCAL to preserve password capability
    User reloaded = userRepository.findById(savedLocalUser.getId()).orElseThrow();
    assertThat(reloaded.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(reloaded.getOauthId()).isEqualTo(LOCAL_GOOGLE_SUB);
  }
}
