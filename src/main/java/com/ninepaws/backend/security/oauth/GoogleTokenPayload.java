package com.ninepaws.backend.security.oauth;

public record GoogleTokenPayload(
    String sub, String email, String name, String picture, boolean emailVerified) {}
