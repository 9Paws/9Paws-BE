package com.ninepaws.backend.security;

import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class SecurityExceptionHandlersTest {

  @Mock private HandlerExceptionResolver resolver;

  @Test
  @DisplayName(
      "CustomAuthenticationEntryPoint forwards AuthenticationException to HandlerExceptionResolver")
  void authenticationEntryPoint_delegatesToResolver() {
    CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(resolver);
    HttpServletRequest request = new MockHttpServletRequest();
    HttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException exception = new BadCredentialsException("Invalid credentials");

    entryPoint.commence(request, response, exception);

    verify(resolver).resolveException(request, response, null, exception);
  }

  @Test
  @DisplayName(
      "CustomAccessDeniedHandler forwards AccessDeniedException to HandlerExceptionResolver")
  void accessDeniedHandler_delegatesToResolver() {
    CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler(resolver);
    HttpServletRequest request = new MockHttpServletRequest();
    HttpServletResponse response = new MockHttpServletResponse();
    AccessDeniedException exception = new AccessDeniedException("Access denied");

    handler.handle(request, response, exception);

    verify(resolver).resolveException(request, response, null, exception);
  }
}
