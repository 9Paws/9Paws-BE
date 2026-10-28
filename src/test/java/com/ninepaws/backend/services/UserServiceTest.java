package com.ninepaws.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.dtos.user.UserProfileResponse;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.exceptions.BaseAppException;
import com.ninepaws.backend.exceptions.error.UserErrorCode;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.oauth.GoogleTokenVerifier;
import com.ninepaws.backend.services.impl.UserServiceImpl;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private GoogleTokenVerifier googleTokenVerifier;

  @InjectMocks private UserServiceImpl userService;

  @Test
  @DisplayName("Should successfully retrieve user profile with companion days calculation")
  void shouldRetrieveUserProfileWithCompanionDays() {
    UUID userId = UUID.randomUUID();
    Instant createdAt = Instant.now().minus(142, ChronoUnit.DAYS);

    User user =
        User.builder()
            .id(userId)
            .fullName("Mai Nguyễn")
            .email("mai.nguyen@email.com")
            .phoneNumber("0987654321")
            .avatarUrl("https://example.com/avatar.jpg")
            .authProvider(AuthProvider.LOCAL)
            .oauthId(null)
            .emailVerified(true)
            .biometricEnabled(true)
            .dailyReminderEnabled(true)
            .dailyReminderTime(LocalTime.of(21, 0))
            .currency("VND")
            .language("vi")
            .build();
    user.setCreatedAt(createdAt);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserProfileResponse response = userService.getCurrentUserProfile(userId);

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(userId);
    assertThat(response.fullName()).isEqualTo("Mai Nguyễn");
    assertThat(response.email()).isEqualTo("mai.nguyen@email.com");
    assertThat(response.phoneNumber()).isEqualTo("0987654321");
    assertThat(response.avatarUrl()).isEqualTo("https://example.com/avatar.jpg");
    assertThat(response.authProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(response.isGoogleLinked()).isFalse();
    assertThat(response.emailVerified()).isTrue();
    assertThat(response.companionDays()).isEqualTo(142L);
    assertThat(response.settings()).isNotNull();
    assertThat(response.settings().biometricEnabled()).isTrue();
    assertThat(response.settings().dailyReminderEnabled()).isTrue();
    assertThat(response.settings().dailyReminderTime()).isEqualTo("21:00");
    assertThat(response.settings().currency()).isEqualTo("VND");
    assertThat(response.settings().language()).isEqualTo("vi");
  }

  @Test
  @DisplayName("Should show isGoogleLinked as true when oauthId is present")
  void shouldShowGoogleLinkedTrueWhenOauthIdPresent() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .fullName("Google Linked User")
            .email("google.linked@email.com")
            .authProvider(AuthProvider.GOOGLE)
            .oauthId("google-sub-xyz")
            .emailVerified(true)
            .build();
    user.setCreatedAt(Instant.now());

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserProfileResponse response = userService.getCurrentUserProfile(userId);

    assertThat(response.isGoogleLinked()).isTrue();
    assertThat(response.companionDays()).isZero();
  }

  @Test
  @DisplayName("Should throw USER_NOT_FOUND when user ID does not exist")
  void shouldThrowWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getCurrentUserProfile(userId))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(UserErrorCode.USER_NOT_FOUND);
  }
}
