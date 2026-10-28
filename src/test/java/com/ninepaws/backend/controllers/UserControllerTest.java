package com.ninepaws.backend.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class UserControllerTest {

  @Mock private UserService userService;

  @InjectMocks private UserController userController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(userController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("GET /api/v1/users/me - Returns 200 OK with UserProfileResponse")
  void shouldReturnUserProfile() throws Exception {
    UUID userId = UUID.randomUUID();
    UserPrincipal principal =
        new UserPrincipal(userId, "mai.nguyen@email.com", "pass", true, Collections.emptyList());
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

    UserSettingsDto settings = new UserSettingsDto(true, true, "21:00", "VND", "vi");
    UserProfileResponse profile =
        new UserProfileResponse(
            userId,
            "Mai Nguyễn",
            "mai.nguyen@email.com",
            "0987654321",
            "https://avatar.png",
            AuthProvider.LOCAL,
            false,
            true,
            142L,
            settings,
            Instant.now());

    when(userService.getCurrentUserProfile(userId)).thenReturn(profile);

    mockMvc
        .perform(get("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.fullName").value("Mai Nguyễn"))
        .andExpect(jsonPath("$.data.companionDays").value(142))
        .andExpect(jsonPath("$.data.settings.dailyReminderTime").value("21:00"))
        .andExpect(jsonPath("$.data.isGoogleLinked").value(false));
  }
}
