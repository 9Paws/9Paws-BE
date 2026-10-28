package com.ninepaws.backend.services.impl;

import com.ninepaws.backend.dtos.user.LinkGoogleAccountResponse;
import com.ninepaws.backend.dtos.user.UpdateProfileRequest;
import com.ninepaws.backend.dtos.user.UpdateSettingsRequest;
import com.ninepaws.backend.dtos.user.UserProfileResponse;
import com.ninepaws.backend.dtos.user.UserSettingsDto;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.exceptions.BaseAppException;
import com.ninepaws.backend.exceptions.error.AuthErrorCode;
import com.ninepaws.backend.exceptions.error.UserErrorCode;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.security.oauth.GoogleTokenPayload;
import com.ninepaws.backend.security.oauth.GoogleTokenVerifier;
import com.ninepaws.backend.services.UserService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final UserRepository userRepository;
  private final GoogleTokenVerifier googleTokenVerifier;

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getCurrentUserProfile(UUID userId) {
    if (userId == null) {
      throw new BaseAppException(UserErrorCode.USER_NOT_FOUND);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(UserErrorCode.USER_NOT_FOUND));

    long companionDays = 0;
    if (user.getCreatedAt() != null) {
      LocalDate createdDate = user.getCreatedAt().atZone(BUSINESS_ZONE).toLocalDate();
      long days = ChronoUnit.DAYS.between(createdDate, LocalDate.now(BUSINESS_ZONE));
      companionDays = Math.max(0, days);
    }

    String reminderTime =
        user.getDailyReminderTime() != null
            ? user.getDailyReminderTime().format(TIME_FORMATTER)
            : "21:00";

    UserSettingsDto settings =
        new UserSettingsDto(
            user.isBiometricEnabled(),
            user.isDailyReminderEnabled(),
            reminderTime,
            user.getCurrency(),
            user.getLanguage());

    boolean isGoogleLinked = user.getOauthId() != null && !user.getOauthId().isBlank();

    return new UserProfileResponse(
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        user.getPhoneNumber(),
        user.getAvatarUrl(),
        user.getAuthProvider(),
        isGoogleLinked,
        user.isEmailVerified(),
        companionDays,
        settings,
        user.getCreatedAt());
  }

  @Override
  @Transactional
  public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    if (userId == null) {
      throw new BaseAppException(UserErrorCode.USER_NOT_FOUND);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(UserErrorCode.USER_NOT_FOUND));

    user.setFullName(request.fullName());
    user.setPhoneNumber(request.phoneNumber());
    userRepository.save(user);
    log.info("Updated profile for user {}: fullName={}", userId, user.getFullName());

    return getCurrentUserProfile(userId);
  }

  @Override
  @Transactional
  public UserSettingsDto updateSettings(UUID userId, UpdateSettingsRequest request) {
    if (userId == null) {
      throw new BaseAppException(UserErrorCode.USER_NOT_FOUND);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(UserErrorCode.USER_NOT_FOUND));

    if (request.biometricEnabled() != null) {
      user.setBiometricEnabled(request.biometricEnabled());
    }
    if (request.dailyReminderEnabled() != null) {
      user.setDailyReminderEnabled(request.dailyReminderEnabled());
    }
    if (request.dailyReminderTime() != null && !request.dailyReminderTime().isBlank()) {
      user.setDailyReminderTime(LocalTime.parse(request.dailyReminderTime(), TIME_FORMATTER));
    }
    if (request.currency() != null && !request.currency().isBlank()) {
      user.setCurrency(request.currency());
    }
    if (request.language() != null && !request.language().isBlank()) {
      user.setLanguage(request.language());
    }

    userRepository.save(user);
    log.info("Updated settings for user {}", userId);

    String reminderTime =
        user.getDailyReminderTime() != null
            ? user.getDailyReminderTime().format(TIME_FORMATTER)
            : "21:00";

    return new UserSettingsDto(
        user.isBiometricEnabled(),
        user.isDailyReminderEnabled(),
        reminderTime,
        user.getCurrency(),
        user.getLanguage());
  }

  @Override
  @Transactional
  public LinkGoogleAccountResponse linkGoogleAccount(UUID userId, String idToken) {
    if (userId == null) {
      throw new BaseAppException(UserErrorCode.USER_NOT_FOUND);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(UserErrorCode.USER_NOT_FOUND));

    if (user.getOauthId() != null && !user.getOauthId().isBlank()) {
      throw new BaseAppException(AuthErrorCode.AUTH_ACCOUNT_ALREADY_LINKED);
    }

    GoogleTokenPayload payload = googleTokenVerifier.verify(idToken);

    if (!user.getEmail().equalsIgnoreCase(payload.email())) {
      throw new BaseAppException(AuthErrorCode.AUTH_EMAIL_MISMATCH);
    }

    if (userRepository.existsByOauthId(payload.sub())) {
      throw new BaseAppException(AuthErrorCode.AUTH_OAUTH_ACCOUNT_ALREADY_IN_USE);
    }

    user.setOauthId(payload.sub());
    userRepository.save(user);
    log.info("Successfully linked Google sub {} to user {}", payload.sub(), userId);

    return new LinkGoogleAccountResponse(true, user.getEmail());
  }
}
