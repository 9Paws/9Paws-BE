package com.ninepaws.backend.dtos.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
        String fullName,
    @Pattern(
            regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$",
            message = "Số điện thoại không đúng định dạng Việt Nam")
        String phoneNumber) {}
