package com.ninepaws.backend.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

  private SecurityUtils() {
    // Utility class: prevent instantiation
  }

  public static UserPrincipal getCurrentUserPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserPrincipal userPrincipal) {
      return userPrincipal;
    }
    return null;
  }

  public static Optional<UserPrincipal> getCurrentUserPrincipalOptional() {
    return Optional.ofNullable(getCurrentUserPrincipal());
  }

  public static UUID getCurrentUserId() {
    UserPrincipal userPrincipal = getCurrentUserPrincipal();
    if (userPrincipal != null) {
      return userPrincipal.getId();
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof UUID uuid) {
        return uuid;
      }
      if (principal instanceof String str) {
        try {
          return UUID.fromString(str);
        } catch (IllegalArgumentException ignored) {
          // Non-UUID string principal (e.g. anonymousUser)
        }
      }
    }

    return null;
  }

  public static Optional<UUID> getCurrentUserIdOptional() {
    return Optional.ofNullable(getCurrentUserId());
  }
}
