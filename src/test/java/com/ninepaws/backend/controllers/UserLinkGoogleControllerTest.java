package com.ninepaws.backend.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninepaws.backend.dtos.auth.GoogleLoginRequest;
import com.ninepaws.backend.dtos.user.LinkGoogleAccountResponse;
import com.ninepaws.backend.exceptions.GlobalExceptionHandler;
import com.ninepaws.backend.security.UserPrincipal;
import com.ninepaws.backend.services.UserService;
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
class UserLinkGoogleControllerTest {

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
  @DisplayName("POST /api/v1/users/me/link-google - Successfully links Google account")
  void shouldReturn200OnLinkGoogleSuccess() throws Exception {
    GoogleLoginRequest request = new GoogleLoginRequest("valid.google.id.token");
    LinkGoogleAccountResponse response =
        new LinkGoogleAccountResponse(true, "mai.nguyen@email.com");

    when(userService.linkGoogleAccount(eq(currentUserId), anyString())).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/users/me/link-google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.isGoogleLinked").value(true))
        .andExpect(jsonPath("$.data.email").value("mai.nguyen@email.com"));
  }

  @Test
  @DisplayName("POST /api/v1/users/me/link-google - Blank token returns 400 Bad Request")
  void shouldReturn400WhenTokenIsBlank() throws Exception {
    GoogleLoginRequest request = new GoogleLoginRequest("");

    mockMvc
        .perform(
            post("/api/v1/users/me/link-google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
