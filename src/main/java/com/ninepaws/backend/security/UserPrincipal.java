package com.ninepaws.backend.security;

import com.ninepaws.backend.entities.User;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class UserPrincipal implements UserDetails, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private static final String DEFAULT_ROLE = "ROLE_USER";

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final boolean emailVerified;
  private final Collection<? extends GrantedAuthority> authorities;

  public UserPrincipal(
      UUID id,
      String email,
      String passwordHash,
      boolean emailVerified,
      Collection<? extends GrantedAuthority> authorities) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.emailVerified = emailVerified;
    this.authorities =
        authorities != null
            ? Collections.unmodifiableCollection(authorities)
            : Collections.singletonList(new SimpleGrantedAuthority(DEFAULT_ROLE));
  }

  public static UserPrincipal create(User user) {
    List<GrantedAuthority> authorities =
        Collections.singletonList(new SimpleGrantedAuthority(DEFAULT_ROLE));
    return new UserPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), user.isEmailVerified(), authorities);
  }

  public static UserPrincipal from(User user) {
    return create(user);
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UserPrincipal that = (UserPrincipal) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "UserPrincipal{"
        + "id="
        + id
        + ", email='"
        + email
        + '\''
        + ", emailVerified="
        + emailVerified
        + ", authorities="
        + authorities
        + '}';
  }
}
