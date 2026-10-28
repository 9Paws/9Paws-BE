package com.ninepaws.backend.dtos.user;

public record UserSettingsDto(
    boolean biometricEnabled,
    boolean dailyReminderEnabled,
    String dailyReminderTime,
    String currency,
    String language) {}
