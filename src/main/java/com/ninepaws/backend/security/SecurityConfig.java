package com.ninepaws.backend.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final CustomAuthenticationEntryPoint authenticationEntryPoint;
  private final CustomAccessDeniedHandler accessDeniedHandler;
  private final String allowedOrigins;

  @Autowired
  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      CustomAuthenticationEntryPoint authenticationEntryPoint,
      CustomAccessDeniedHandler accessDeniedHandler,
      @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
          String allowedOrigins) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
    this.allowedOrigins = allowedOrigins;
  }

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this(jwtAuthenticationFilter, null, null, "http://localhost:*,http://127.0.0.1:*");
  }

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, String allowedOrigins) {
    this(jwtAuthenticationFilter, null, null, allowedOrigins);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions -> {
              if (authenticationEntryPoint != null) {
                exceptions.authenticationEntryPoint(authenticationEntryPoint);
              }
              if (accessDeniedHandler != null) {
                exceptions.accessDeniedHandler(accessDeniedHandler);
              }
            })
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/verify-registration-otp",
                        "/api/v1/auth/resend-otp",
                        "/api/v1/auth/refresh-token",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/verify-forgot-password-otp",
                        "/api/v1/auth/reset-password",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/docs",
                        "/docs/**",
                        "/scalar",
                        "/scalar/**",
                        "/actuator/health",
                        "/api/v1/public/split-bills/*",
                        "/api/v1/public/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/logout",
                        "/api/v1/auth/logout-all",
                        "/api/v1/auth/change-password")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> origins =
        (allowedOrigins == null || allowedOrigins.isBlank())
            ? List.of("http://localhost:*", "http://127.0.0.1:*")
            : Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    configuration.setAllowedOriginPatterns(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
