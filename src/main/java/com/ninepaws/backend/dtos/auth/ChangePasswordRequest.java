package com.ninepaws.backend.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
    @NotBlank(message = "Mật khẩu hiện tại không được để trống") String currentPassword,
    @NotBlank(message = "Mật khẩu mới không được để trống")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message =
                "Mật khẩu mới phải có ít nhất 8 ký tự, bao gồm ít nhất một chữ cái và một chữ số")
        String newPassword,
    @NotBlank(message = "Xác nhận mật khẩu không được để trống") String confirmPassword) {}
