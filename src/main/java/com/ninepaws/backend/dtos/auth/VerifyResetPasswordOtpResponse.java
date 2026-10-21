package com.ninepaws.backend.dtos.auth;

public record VerifyResetPasswordOtpResponse(String email, String resetToken) {}
