package com.ninepaws.backend.security;

import com.ninepaws.backend.repositories.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    log.debug("Loading user by email: {}", email);
    return userRepository
        .findByEmail(email)
        .map(UserPrincipal::create)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  @Transactional(readOnly = true)
  public UserPrincipal loadUserById(UUID userId) throws UsernameNotFoundException {
    log.debug("Loading user by id: {}", userId);
    return userRepository
        .findById(userId)
        .map(UserPrincipal::create)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
  }
}
