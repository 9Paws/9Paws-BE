package com.ninepaws.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ninepaws.backend.services.TokenRevocationService;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock
  private JwtService jwtService;

  @Mock
  private TokenRevocationService tokenRevocationService;

  @Mock
  private CustomUserDetailsService userDetailsService;

  @InjectMocks
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  private UserPrincipal mockPrincipal;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    mockPrincipal =
        new UserPrincipal(
            UUID.randomUUID(),
            "user@example.com",
            "hashed-pw",
            true,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Valid access token and not blacklisted sets SecurityContext authentication")
  void whenValidAccessTokenAndNotBlacklisted_setsSecurityContextAuthentication() throws Exception {
    String token = "valid-token";
    TokenPayload payload =
        new TokenPayload(
            UUID.randomUUID(),
            "user@example.com",
            "jti-1",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(1800));
    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-1")).thenReturn(false);
    when(userDetailsService.loadUserById(payload.userId())).thenReturn(mockPrincipal);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(mockPrincipal);
  }

  @Test
  @DisplayName("Blacklisted access token does not set authentication")
  void whenAccessTokenIsBlacklisted_doesNotSetAuthentication() throws Exception {
    String token = "revoked-token";
    TokenPayload payload =
        new TokenPayload(
            UUID.randomUUID(),
            "user@example.com",
            "jti-blacklisted",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(1800));
    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-blacklisted")).thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(userDetailsService, never()).loadUserById(any());
  }

  @Test
  @DisplayName("Missing Authorization header does not set authentication and continues chain")
  void whenNoAuthorizationHeader_doesNotSetAuthenticationAndContinuesChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(jwtService, never()).parseAndValidateToken(any());
  }

  @Test
  @DisplayName("Non-Bearer Authorization header does not set authentication and continues chain")
  void whenAuthorizationHeaderNotBearer_doesNotSetAuthenticationAndContinuesChain()
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(jwtService, never()).parseAndValidateToken(any());
  }

  @Test
  @DisplayName("Bearer header with blank token does not set authentication and continues chain")
  void whenBearerTokenIsBlank_doesNotSetAuthenticationAndContinuesChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer   ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(jwtService, never()).parseAndValidateToken(any());
  }

  @Test
  @DisplayName("Token type REFRESH does not set authentication")
  void whenTokenTypeIsNotAccess_doesNotSetAuthentication() throws Exception {
    String token = "refresh-token";
    TokenPayload payload =
        new TokenPayload(
            UUID.randomUUID(),
            "user@example.com",
            "jti-ref",
            "REFRESH",
            Instant.now(),
            Instant.now().plusSeconds(604800));
    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(tokenRevocationService, never()).isAccessTokenBlacklisted(any());
    verify(userDetailsService, never()).loadUserById(any());
  }

  @Test
  @DisplayName("JwtException during parsing does not set authentication and clears context")
  void whenJwtServiceThrowsException_doesNotSetAuthenticationAndClearsContext() throws Exception {
    String token = "invalid-token";
    when(jwtService.parseAndValidateToken(token))
        .thenThrow(new JwtException("Token expired or corrupted"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(userDetailsService, never()).loadUserById(any());
  }

  @Test
  @DisplayName("UsernameNotFoundException when loading user does not set authentication")
  void whenUserDetailsServiceThrowsException_doesNotSetAuthentication() throws Exception {
    String token = "valid-token-user-deleted";
    TokenPayload payload =
        new TokenPayload(
            UUID.randomUUID(),
            "deleted@example.com",
            "jti-2",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(1800));
    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-2")).thenReturn(false);
    when(userDetailsService.loadUserById(payload.userId()))
        .thenThrow(new UsernameNotFoundException("User not found"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("When SecurityContext already has authentication, does not reload user")
  void whenAuthenticationAlreadyExists_doesNotReloadUser() throws Exception {
    UsernamePasswordAuthenticationToken existingAuth =
        new UsernamePasswordAuthenticationToken(
            mockPrincipal, null, mockPrincipal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(existingAuth);

    String token = "valid-token";
    TokenPayload payload =
        new TokenPayload(
            mockPrincipal.getId(),
            mockPrincipal.getEmail(),
            "jti-existing",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(1800));
    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-existing")).thenReturn(false);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
    verify(userDetailsService, never()).loadUserById(any());
  }

  @Nested
  @DisplayName("SecurityConfig")
  class SecurityConfigTests {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUpConfig() {
      securityConfig = new SecurityConfig(jwtAuthenticationFilter);
    }

    @Test
    @DisplayName("passwordEncoder returns BCryptPasswordEncoder with strength 12")
    void passwordEncoder_returnsBcryptPasswordEncoder() {
      PasswordEncoder encoder = securityConfig.passwordEncoder();
      assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);

      String rawPassword = "ValidPassword123!";
      String encoded = encoder.encode(rawPassword);

      assertThat(encoder.matches(rawPassword, encoded)).isTrue();
      assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    @DisplayName("corsConfigurationSource configures open CORS for API clients")
    void corsConfigurationSource_returnsValidConfig() {
      CorsConfigurationSource source = securityConfig.corsConfigurationSource();
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setRequestURI("/api/v1/auth/login");
      CorsConfiguration config = source.getCorsConfiguration(request);

      assertThat(config).isNotNull();
      assertThat(config.getAllowCredentials()).isTrue();
      assertThat(config.getAllowedOriginPatterns()).contains("*");
      assertThat(config.getAllowedMethods())
          .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
  }
}
