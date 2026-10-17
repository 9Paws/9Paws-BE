package com.ninepaws.backend.dtos.auth;

import com.ninepaws.backend.enums.OtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResendOtpRequest(@NotBlank @Email String email, @NotNull OtpType type) {}
