package com.ninepaws.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityUtilsTest {

  @BeforeEach
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("getCurrentUserPrincipal")
  class GetCurrentUserPrincipalTests {

    @Test
    @DisplayName("returns UserPrincipal when authenticated in SecurityContext")
    void getCurrentUserPrincipal_whenAuthenticated_returnsPrincipal() {
      UUID userId = UUID.randomUUID();
      UserPrincipal principal =
          new UserPrincipal(userId, "test@example.com", "hash", true, Collections.emptyList());
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
      SecurityContextHolder.getContext().setAuthentication(auth);

      UserPrincipal result = SecurityUtils.getCurrentUserPrincipal();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(userId);
      assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("returns null when SecurityContext has no authentication")
    void getCurrentUserPrincipal_whenNoAuth_returnsNull() {
      SecurityContextHolder.getContext().setAuthentication(null);

      assertThat(SecurityUtils.getCurrentUserPrincipal()).isNull();
    }

    @Test
    @DisplayName("returns null when authentication is not authenticated")
    void getCurrentUserPrincipal_whenNotAuthenticated_returnsNull() {
      UUID userId = UUID.randomUUID();
      UserPrincipal principal =
          new UserPrincipal(userId, "test@example.com", "hash", true, Collections.emptyList());
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(principal, null);
      auth.setAuthenticated(false);
      SecurityContextHolder.getContext().setAuthentication(auth);

      assertThat(SecurityUtils.getCurrentUserPrincipal()).isNull();
    }

    @Test
    @DisplayName("returns null when principal is anonymous string")
    void getCurrentUserPrincipal_whenPrincipalIsAnonymous_returnsNull() {
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(auth);

      assertThat(SecurityUtils.getCurrentUserPrincipal()).isNull();
    }
  }

  @Nested
  @DisplayName("getCurrentUserId")
  class GetCurrentUserIdTests {

    @Test
    @DisplayName("returns userId when authenticated with UserPrincipal")
    void getCurrentUserId_whenUserPrincipal_returnsId() {
      UUID userId = UUID.randomUUID();
      UserPrincipal principal =
          new UserPrincipal(userId, "test@example.com", "hash", true, Collections.emptyList());
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
      SecurityContextHolder.getContext().setAuthentication(auth);

      UUID result = SecurityUtils.getCurrentUserId();

      assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("returns userId when principal is directly a UUID")
    void getCurrentUserId_whenPrincipalIsUuid_returnsId() {
      UUID userId = UUID.randomUUID();
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(auth);

      UUID result = SecurityUtils.getCurrentUserId();

      assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("returns userId when principal is a UUID string")
    void getCurrentUserId_whenPrincipalIsUuidString_returnsParsedId() {
      UUID userId = UUID.randomUUID();
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(auth);

      UUID result = SecurityUtils.getCurrentUserId();

      assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("returns null when principal is non-UUID string like anonymousUser")
    void getCurrentUserId_whenPrincipalIsNonUuidString_returnsNull() {
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(auth);

      assertThat(SecurityUtils.getCurrentUserId()).isNull();
    }

    @Test
    @DisplayName("returns null when no authentication exists")
    void getCurrentUserId_whenNoAuth_returnsNull() {
      SecurityContextHolder.getContext().setAuthentication(null);

      assertThat(SecurityUtils.getCurrentUserId()).isNull();
    }
  }
}
