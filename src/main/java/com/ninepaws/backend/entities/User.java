package com.ninepaws.backend.entities;

import com.ninepaws.backend.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseAuditEntity {

  @Column(name = "full_name", nullable = false, length = 100)
  private String fullName;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_provider", nullable = false, length = 20)
  @Builder.Default
  private AuthProvider authProvider = AuthProvider.LOCAL;

  @Column(name = "avatar_url", columnDefinition = "TEXT")
  private String avatarUrl;

  @Column(name = "email_verified", nullable = false)
  @Builder.Default
  private boolean emailVerified = false;

  @Column(name = "biometric_enabled", nullable = false)
  @Builder.Default
  private boolean biometricEnabled = false;

  @Column(name = "daily_reminder_enabled", nullable = false)
  @Builder.Default
  private boolean dailyReminderEnabled = true;

  @Column(name = "daily_reminder_time", nullable = false)
  @Builder.Default
  private LocalTime dailyReminderTime = LocalTime.of(21, 0);

  @Column(name = "currency", nullable = false, length = 10)
  @Builder.Default
  private String currency = "VND";

  @Column(name = "language", nullable = false, length = 10)
  @Builder.Default
  private String language = "vi";
}
