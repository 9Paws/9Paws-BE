package com.ninepaws.backend.dtos.auth;

import com.ninepaws.backend.enums.OtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResendOtpRequest(
    @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        String email,
    @NotNull(message = "Loại OTP không được để trống") OtpType type) {}
