package com.ninepaws.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninepaws.backend.dtos.user.UpdateProfileRequest;
import com.ninepaws.backend.dtos.user.UpdateSettingsRequest;
import com.ninepaws.backend.dtos.user.UserProfileResponse;
import com.ninepaws.backend.dtos.user.UserSettingsDto;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.exceptions.GlobalExceptionHandler;
import com.ninepaws.backend.security.UserPrincipal;
import com.ninepaws.backend.services.UserService;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerUpdateTest {

  @Mock private UserService userService;

  @InjectMocks private UserController userController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private UUID currentUserId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(userController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    objectMapper = new ObjectMapper();

    currentUserId = UUID.randomUUID();
    UserPrincipal principal =
        new UserPrincipal(
            currentUserId, "mai.nguyen@email.com", "pass", true, Collections.emptyList());
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("PUT /api/v1/users/me - Updates full name and phone number successfully")
  void shouldUpdateProfileSuccessfully() throws Exception {
    UpdateProfileRequest request = new UpdateProfileRequest("Mai Nguyễn Updated", "0987654321");
    UserSettingsDto settings = new UserSettingsDto(false, true, "21:00", "VND", "vi");
    UserProfileResponse updatedProfile =
        new UserProfileResponse(
            currentUserId,
            "Mai Nguyễn Updated",
            "mai.nguyen@email.com",
            "0987654321",
            null,
            AuthProvider.LOCAL,
            false,
            true,
            10L,
            settings,
            Instant.now());

    when(userService.updateProfile(eq(currentUserId), any(UpdateProfileRequest.class)))
        .thenReturn(updatedProfile);

    mockMvc
        .perform(
            put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.fullName").value("Mai Nguyễn Updated"))
        .andExpect(jsonPath("$.data.phoneNumber").value("0987654321"));
  }

  @Test
  @DisplayName("PUT /api/v1/users/me - Invalid phone number format returns 400 Bad Request")
  void shouldReturn400OnInvalidPhoneNumber() throws Exception {
    UpdateProfileRequest request = new UpdateProfileRequest("Mai Nguyễn", "12345");

    mockMvc
        .perform(
            put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT /api/v1/users/me - Blank full name returns 400 Bad Request")
  void shouldReturn400OnBlankFullName() throws Exception {
    UpdateProfileRequest request = new UpdateProfileRequest("", "0987654321");

    mockMvc
        .perform(
            put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PATCH /api/v1/users/me/settings - Updates settings successfully")
  void shouldUpdateSettingsSuccessfully() throws Exception {
    UpdateSettingsRequest request =
        new UpdateSettingsRequest(true, true, "21:30", "VND", "vi");
    UserSettingsDto updatedSettings = new UserSettingsDto(true, true, "21:30", "VND", "vi");

    when(userService.updateSettings(eq(currentUserId), any(UpdateSettingsRequest.class)))
        .thenReturn(updatedSettings);

    mockMvc
        .perform(
            patch("/api/v1/users/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.biometricEnabled").value(true))
        .andExpect(jsonPath("$.data.dailyReminderTime").value("21:30"));
  }

  @Test
  @DisplayName("PATCH /api/v1/users/me/settings - Invalid reminder time returns 400 Bad Request")
  void shouldReturn400OnInvalidReminderTime() throws Exception {
    UpdateSettingsRequest request =
        new UpdateSettingsRequest(null, null, "25:99", null, null);

    mockMvc
        .perform(
            patch("/api/v1/users/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
