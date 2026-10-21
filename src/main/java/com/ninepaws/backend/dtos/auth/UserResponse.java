package com.ninepaws.backend.dtos.auth;

import java.util.UUID;

public record UserResponse(
    UUID id,
    String fullName,
    String email,
    String phoneNumber,
    String avatarUrl,
    boolean emailVerified,
    String currency,
    String language) {}
