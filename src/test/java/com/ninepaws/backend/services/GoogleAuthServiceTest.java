package com.ninepaws.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.dtos.auth.AuthResponse;
import com.ninepaws.backend.dtos.auth.GoogleLoginRequest;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.exceptions.BaseAppException;
import com.ninepaws.backend.exceptions.error.AuthErrorCode;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.JwtService;
import com.ninepaws.backend.security.oauth.GoogleTokenPayload;
import com.ninepaws.backend.security.oauth.GoogleTokenVerifier;
import com.ninepaws.backend.services.impl.AuthServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private OtpService otpService;
  @Mock private EmailService emailService;
  @Mock private TokenRevocationService tokenRevocationService;
  @Mock private GoogleTokenVerifier googleTokenVerifier;

  @InjectMocks private AuthServiceImpl authService;

  @Test
  @DisplayName("Should successfully login returning user by oauth_id")
  void shouldLoginReturningUserByOauthId() {
    String idToken = "valid.google.id.token";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            "google-sub-12345", "returning@gmail.com", "Returning User", "https://avatar.png", true);
    when(googleTokenVerifier.verify(idToken)).thenReturn(payload);

    UUID userId = UUID.randomUUID();
    User existingUser =
        User.builder()
            .id(userId)
            .fullName("Returning User")
            .email("returning@gmail.com")
            .authProvider(AuthProvider.GOOGLE)
            .oauthId("google-sub-12345")
            .emailVerified(true)
            .currency("VND")
            .language("vi")
            .build();

    when(userRepository.findByOauthId("google-sub-12345")).thenReturn(Optional.of(existingUser));
    when(jwtService.generateAccessToken(eq(userId), eq("returning@gmail.com"), anyString()))
        .thenReturn("access.jwt.token");
    when(jwtService.generateRefreshToken(eq(userId), anyString())).thenReturn("refresh.jwt.token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(1800L);
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

    AuthResponse response = authService.loginWithGoogle(new GoogleLoginRequest(idToken));

    assertThat(response).isNotNull();
    assertThat(response.accessToken()).isEqualTo("access.jwt.token");
    assertThat(response.refreshToken()).isEqualTo("refresh.jwt.token");
    assertThat(response.user().email()).isEqualTo("returning@gmail.com");
    verify(tokenRevocationService).recordRefreshToken(eq(userId), anyString(), eq("refresh.jwt.token"), anyLong());
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("Should register and login new user when neither oauth_id nor email exists")
  void shouldRegisterAndLoginNewUser() {
    String idToken = "new.google.id.token";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            "google-sub-new", "newbie@gmail.com", "New User", "https://avatar.png", true);
    when(googleTokenVerifier.verify(idToken)).thenReturn(payload);
    when(userRepository.findByOauthId("google-sub-new")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("newbie@gmail.com")).thenReturn(Optional.empty());

    UUID generatedId = UUID.randomUUID();
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User toSave = invocation.getArgument(0);
              toSave.setId(generatedId);
              return toSave;
            });

    when(jwtService.generateAccessToken(eq(generatedId), eq("newbie@gmail.com"), anyString()))
        .thenReturn("access.jwt.token");
    when(jwtService.generateRefreshToken(eq(generatedId), anyString())).thenReturn("refresh.jwt.token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(1800L);
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

    AuthResponse response = authService.loginWithGoogle(new GoogleLoginRequest(idToken));

    assertThat(response).isNotNull();
    assertThat(response.accessToken()).isEqualTo("access.jwt.token");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User saved = userCaptor.getValue();
    assertThat(saved.getFullName()).isEqualTo("New User");
    assertThat(saved.getEmail()).isEqualTo("newbie@gmail.com");
    assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(saved.getOauthId()).isEqualTo("google-sub-new");
    assertThat(saved.isEmailVerified()).isTrue();
    assertThat(saved.getAvatarUrl()).isEqualTo("https://avatar.png");
  }

  @Test
  @DisplayName("Anti-Account Takeover: Should reject with 409 Conflict when email matches local account without oauth_id")
  void shouldRejectWhenEmailMatchesLocalAccountWithoutOauthId() {
    String idToken = "collision.google.id.token";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            "google-sub-attacker", "victim@gmail.com", "Attacker Name", null, true);
    when(googleTokenVerifier.verify(idToken)).thenReturn(payload);
    when(userRepository.findByOauthId("google-sub-attacker")).thenReturn(Optional.empty());

    User existingLocalUser =
        User.builder()
            .id(UUID.randomUUID())
            .fullName("Victim User")
            .email("victim@gmail.com")
            .authProvider(AuthProvider.LOCAL)
            .oauthId(null)
            .emailVerified(true)
            .build();
    when(userRepository.findByEmail("victim@gmail.com")).thenReturn(Optional.of(existingLocalUser));

    assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest(idToken)))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_ACCOUNT_LINKING_REQUIRED);

    verify(userRepository, never()).save(any(User.class));
    verify(tokenRevocationService, never()).recordRefreshToken(any(), any(), any(), anyLong());
  }
}
