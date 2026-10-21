package com.ninepaws.backend.dtos.auth;

public record AuthResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
