package com.ninepaws.backend.dtos.user;

import com.ninepaws.backend.enums.AuthProvider;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String fullName,
    String email,
    String phoneNumber,
    String avatarUrl,
    AuthProvider authProvider,
    boolean isGoogleLinked,
    boolean emailVerified,
    long companionDays,
    UserSettingsDto settings,
    Instant createdAt) {}
