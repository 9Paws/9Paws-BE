package com.ninepaws.backend.dtos.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
    Boolean biometricEnabled,
    Boolean dailyReminderEnabled,
    @Pattern(
            regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
            message = "Giờ nhắc nhở phải đúng định dạng HH:mm")
        String dailyReminderTime,
    @Size(max = 10, message = "Đơn vị tiền tệ không quá 10 ký tự") String currency,
    @Size(max = 10, message = "Mã ngôn ngữ không quá 10 ký tự") String language) {}
