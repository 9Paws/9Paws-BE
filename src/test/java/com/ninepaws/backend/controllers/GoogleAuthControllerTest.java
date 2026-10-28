package com.ninepaws.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninepaws.backend.dtos.auth.AuthResponse;
import com.ninepaws.backend.dtos.auth.GoogleLoginRequest;
import com.ninepaws.backend.dtos.auth.UserResponse;
import com.ninepaws.backend.exceptions.BaseAppException;
import com.ninepaws.backend.exceptions.GlobalExceptionHandler;
import com.ninepaws.backend.exceptions.error.AuthErrorCode;
import com.ninepaws.backend.services.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GoogleAuthControllerTest {

  @Mock private AuthService authService;

  @InjectMocks private AuthController authController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("POST /api/v1/auth/google - Success returns 200 with AuthResponse")
  void shouldReturn200OnGoogleLoginSuccess() throws Exception {
    GoogleLoginRequest request = new GoogleLoginRequest("valid.google.id.token");
    UserResponse userResponse =
        new UserResponse(
            UUID.randomUUID(),
            "Mai Nguyễn",
            "mai.nguyen@gmail.com",
            null,
            "https://avatar.png",
            true,
            "VND",
            "vi");
    AuthResponse authResponse =
        new AuthResponse("access.token", "refresh.token", "Bearer", 1800L, userResponse);

    when(authService.loginWithGoogle(any(GoogleLoginRequest.class))).thenReturn(authResponse);

    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.accessToken").value("access.token"))
        .andExpect(jsonPath("$.data.user.email").value("mai.nguyen@gmail.com"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/google - Blank token returns 400 Bad Request")
  void shouldReturn400WhenTokenIsBlank() throws Exception {
    GoogleLoginRequest request = new GoogleLoginRequest("");

    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/auth/google - Account takeover collision returns 409 Conflict")
  void shouldReturn409WhenAccountLinkingRequired() throws Exception {
    GoogleLoginRequest request = new GoogleLoginRequest("colliding.token");

    when(authService.loginWithGoogle(any(GoogleLoginRequest.class)))
        .thenThrow(new BaseAppException(AuthErrorCode.AUTH_ACCOUNT_LINKING_REQUIRED));

    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("AUTH_107"));
  }
}
