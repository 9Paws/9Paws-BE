package com.ninepaws.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.dtos.user.LinkGoogleAccountResponse;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.exceptions.BaseAppException;
import com.ninepaws.backend.exceptions.error.AuthErrorCode;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.oauth.GoogleTokenPayload;
import com.ninepaws.backend.security.oauth.GoogleTokenVerifier;
import com.ninepaws.backend.services.impl.UserServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLinkGoogleTest {

  @Mock private UserRepository userRepository;
  @Mock private GoogleTokenVerifier googleTokenVerifier;

  @InjectMocks private UserServiceImpl userService;

  @Test
  @DisplayName("Should successfully link Google account when email matches and oauth_id is null")
  void shouldSuccessfullyLinkGoogleAccount() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .fullName("Mai Nguyễn")
            .email("mai.nguyen@email.com")
            .authProvider(AuthProvider.LOCAL)
            .oauthId(null)
            .build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    String idToken = "valid.google.token";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            "google-sub-777", "mai.nguyen@email.com", "Mai Nguyễn", "https://avatar.png", true);
    when(googleTokenVerifier.verify(idToken)).thenReturn(payload);
    when(userRepository.existsByOauthId("google-sub-777")).thenReturn(false);

    LinkGoogleAccountResponse response = userService.linkGoogleAccount(userId, idToken);

    assertThat(response).isNotNull();
    assertThat(response.isGoogleLinked()).isTrue();
    assertThat(response.email()).isEqualTo("mai.nguyen@email.com");
    assertThat(user.getOauthId()).isEqualTo("google-sub-777");
    assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL); // Preserves credential origin!
    verify(userRepository).save(user);
  }

  @Test
  @DisplayName("Should reject with 400 AUTH_ACCOUNT_ALREADY_LINKED if user already has oauthId")
  void shouldRejectIfUserAlreadyLinked() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .fullName("Mai Nguyễn")
            .email("mai.nguyen@email.com")
            .authProvider(AuthProvider.LOCAL)
            .oauthId("existing-sub")
            .build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.linkGoogleAccount(userId, "any.token"))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_ACCOUNT_ALREADY_LINKED);
  }

  @Test
  @DisplayName("Should reject with 400 AUTH_EMAIL_MISMATCH if Google token email differs from user email")
  void shouldRejectWhenEmailMismatch() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .fullName("Mai Nguyễn")
            .email("mai.nguyen@email.com")
            .authProvider(AuthProvider.LOCAL)
            .oauthId(null)
            .build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    String idToken = "different.email.token";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            "google-sub-different", "different@gmail.com", "Different Name", null, true);
    when(googleTokenVerifier.verify(idToken)).thenReturn(payload);

    assertThatThrownBy(() -> userService.linkGoogleAccount(userId, idToken))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_EMAIL_MISMATCH);
  }

  @Test
  @DisplayName("Should reject with 409 AUTH_OAUTH_ACCOUNT_ALREADY_IN_USE if Google sub already attached to another user")
  void shouldRejectWhenGoogleSubAlreadyAttachedToAnotherUser() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .fullName("Mai Nguyễn")
            .email("mai.nguyen@email.com")
            .authProvider(AuthProvider.LOCAL)
            .oauthId(null)
            .build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    String idToken = "stolen.sub.token";
    GoogleTokenPayload payload =
        new GoogleTokenPayload(
            "already-claimed-sub", "mai.nguyen@email.com", "Mai Nguyễn", null, true);
    when(googleTokenVerifier.verify(idToken)).thenReturn(payload);
    when(userRepository.existsByOauthId("already-claimed-sub")).thenReturn(true);

    assertThatThrownBy(() -> userService.linkGoogleAccount(userId, idToken))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_OAUTH_ACCOUNT_ALREADY_IN_USE);
  }
}
