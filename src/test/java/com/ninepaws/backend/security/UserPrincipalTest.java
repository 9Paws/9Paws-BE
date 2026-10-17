package com.ninepaws.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninepaws.backend.entities.User;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserPrincipalTest {

  @Test
  @DisplayName("create from User maps all fields and defaults to ROLE_USER")
  void create_fromUser_mapsFieldsCorrectly() {
    UUID userId = UUID.randomUUID();
    User user = User.builder()
        .fullName("Nguyen Van A")
        .email("nguyenvana@ninepaws.com")
        .passwordHash("hashed-pw-123")
        .emailVerified(true)
        .build();
    user.setId(userId);

    UserPrincipal principal = UserPrincipal.create(user);

    assertThat(principal.getId()).isEqualTo(userId);
    assertThat(principal.getEmail()).isEqualTo("nguyenvana@ninepaws.com");
    assertThat(principal.getUsername()).isEqualTo("nguyenvana@ninepaws.com");
    assertThat(principal.getPassword()).isEqualTo("hashed-pw-123");
    assertThat(principal.getPasswordHash()).isEqualTo("hashed-pw-123");
    assertThat(principal.isEmailVerified()).isTrue();
    assertThat(principal.isEnabled()).isTrue();
    assertThat(principal.isAccountNonExpired()).isTrue();
    assertThat(principal.isAccountNonLocked()).isTrue();
    assertThat(principal.isCredentialsNonExpired()).isTrue();
    assertThat(principal.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("custom constructor assigns provided authorities and fields")
  void customConstructor_assignsAuthoritiesAndFields() {
    UUID userId = UUID.randomUUID();
    List<GrantedAuthority> customAuthorities = List.of(
        new SimpleGrantedAuthority("ROLE_ADMIN"),
        new SimpleGrantedAuthority("ROLE_USER")
    );

    UserPrincipal principal = new UserPrincipal(
        userId,
        "admin@ninepaws.com",
        "admin-hash",
        false,
        customAuthorities
    );

    assertThat(principal.getId()).isEqualTo(userId);
    assertThat(principal.getEmail()).isEqualTo("admin@ninepaws.com");
    assertThat(principal.getUsername()).isEqualTo("admin@ninepaws.com");
    assertThat(principal.getPassword()).isEqualTo("admin-hash");
    assertThat(principal.isEmailVerified()).isFalse();
    assertThat(principal.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  @DisplayName("custom constructor with null authorities defaults to ROLE_USER")
  void customConstructor_withNullAuthorities_defaultsToRoleUser() {
    UUID userId = UUID.randomUUID();
    UserPrincipal principal = new UserPrincipal(
        userId,
        "user@ninepaws.com",
        "hash",
        true,
        null
    );

    assertThat(principal.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("equals and hashCode contract based on user id")
  void equalsAndHashCode_basedOnId() {
    UUID userId = UUID.randomUUID();
    UserPrincipal p1 = new UserPrincipal(userId, "u1@test.com", "h1", true, Collections.emptyList());
    UserPrincipal p2 = new UserPrincipal(userId, "u2@test.com", "h2", false, Collections.emptyList());
    UserPrincipal p3 = new UserPrincipal(UUID.randomUUID(), "u1@test.com", "h1", true, Collections.emptyList());

    assertThat(p1).isEqualTo(p2);
    assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    assertThat(p1).isNotEqualTo(p3);
    assertThat(p1).isNotEqualTo(null);
    assertThat(p1).isNotEqualTo("some string");
  }
}
