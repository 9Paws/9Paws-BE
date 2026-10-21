package com.ninepaws.backend.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
    @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        String email,
    @NotBlank(message = "Reset token không được để trống") String resetToken,
    @NotBlank(message = "Mật khẩu mới không được để trống")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message =
                "Mật khẩu mới phải có ít nhất 8 ký tự, bao gồm ít nhất một chữ cái và một chữ số")
        String newPassword) {}
