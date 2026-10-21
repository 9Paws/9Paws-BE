package com.ninepaws.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.repositories.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private CustomUserDetailsService userDetailsService;

  @Nested
  @DisplayName("loadUserByUsername")
  class LoadUserByUsernameTests {

    @Test
    @DisplayName("returns UserPrincipal when user exists with email")
    void loadUserByUsername_whenUserExists_returnsUserPrincipal() {
      UUID userId = UUID.randomUUID();
      User user =
          User.builder()
              .fullName("Test User")
              .email("user@example.com")
              .passwordHash("hashed-password")
              .emailVerified(true)
              .build();
      user.setId(userId);

      when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

      UserDetails details = userDetailsService.loadUserByUsername("user@example.com");

      assertThat(details).isNotNull();
      assertThat(details.getUsername()).isEqualTo("user@example.com");
      assertThat(details.getPassword()).isEqualTo("hashed-password");
      assertThat(details).isInstanceOf(UserPrincipal.class);
      UserPrincipal principal = (UserPrincipal) details;
      assertThat(principal.getId()).isEqualTo(userId);
      assertThat(principal.isEmailVerified()).isTrue();
      assertThat(principal.getAuthorities()).isNotEmpty();
      verify(userRepository).findByEmail("user@example.com");
    }

    @Test
    @DisplayName("throws UsernameNotFoundException when user does not exist with email")
    void loadUserByUsername_whenNotFound_throwsUsernameNotFoundException() {
      when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userDetailsService.loadUserByUsername("notfound@example.com"))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("notfound@example.com");
      verify(userRepository).findByEmail("notfound@example.com");
    }
  }

  @Nested
  @DisplayName("loadUserById")
  class LoadUserByIdTests {

    @Test
    @DisplayName("returns UserPrincipal when user exists with UUID id")
    void loadUserById_whenUserExists_returnsUserPrincipal() {
      UUID userId = UUID.randomUUID();
      User user =
          User.builder()
              .fullName("Test User")
              .email("iduser@example.com")
              .passwordHash("hashed-password")
              .emailVerified(false)
              .build();
      user.setId(userId);

      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      UserPrincipal principal = userDetailsService.loadUserById(userId);

      assertThat(principal).isNotNull();
      assertThat(principal.getId()).isEqualTo(userId);
      assertThat(principal.getUsername()).isEqualTo("iduser@example.com");
      assertThat(principal.getEmail()).isEqualTo("iduser@example.com");
      assertThat(principal.isEmailVerified()).isFalse();
      verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("throws UsernameNotFoundException when user does not exist with UUID id")
    void loadUserById_whenNotFound_throwsUsernameNotFoundException() {
      UUID missingId = UUID.randomUUID();
      when(userRepository.findById(missingId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userDetailsService.loadUserById(missingId))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining(missingId.toString());
      verify(userRepository).findById(missingId);
    }
  }
}
