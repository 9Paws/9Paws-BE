package com.ninepaws.backend.security;

import java.time.Instant;
import java.util.UUID;

public record TokenPayload(
    UUID userId, String email, String jti, String tokenType, Instant issuedAt, Instant expiresAt) {}
